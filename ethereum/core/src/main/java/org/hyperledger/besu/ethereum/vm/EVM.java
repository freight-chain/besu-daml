/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.vm;

import static org.apache.logging.log4j.LogManager.getLogger;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.MessageFrame.State;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.ethereum.vm.operations.InvalidOperation;
import org.hyperledger.besu.ethereum.vm.operations.StopOperation;
import org.hyperledger.besu.ethereum.vm.operations.VirtualOperation;

public class EVM {
  private static final Logger LOG = getLogger();

  private final OperationRegistry operations;
  private final Operation invalidOperation;
  private final Operation endOfScriptStop;

  public EVM(final OperationRegistry operations,
             final GasCalculator gasCalculator) {
    this.operations = operations;
    this.invalidOperation = new InvalidOperation(gasCalculator);
    this.endOfScriptStop =
        new VirtualOperation(new StopOperation(gasCalculator));
  }

  public void runToHalt(final MessageFrame frame,
                        final OperationTracer operationTracer)
      throws ExceptionalHaltException {
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      executeNextOperation(frame, operationTracer);
    }
  }

  public void
  forEachOperation(final Code code, final int contractAccountVersion,
                   final BiConsumer<Operation, Integer> operationDelegate) {
    int pc = 0;
    final int length = code.getSize();

    while (pc < length) {
      final Operation curOp =
          operationAtOffset(code, contractAccountVersion, pc);
      operationDelegate.accept(curOp, pc);
      pc += curOp.getOpSize();
    }
  }

  private void executeNextOperation(final MessageFrame frame,
                                    final OperationTracer operationTracer)
      throws ExceptionalHaltException {
    frame.setCurrentOperation(operationAtOffset(
        frame.getCode(), frame.getContractAccountVersion(), frame.getPC()));
    frame.setExceptionalHaltReason(checkExceptionalHalt(frame, this));
    final Optional<Gas> currentGasCost = calculateGasCost(frame);
    operationTracer.traceExecution(frame, currentGasCost, () -> {
      logState(frame, currentGasCost);
      checkForExceptionalHalt(frame);
      decrementRemainingGas(frame, currentGasCost);
      frame.getCurrentOperation().execute(frame);
      incrementProgramCounter(frame);
    });
  }

  public static Optional<ExceptionalHaltReason>
  checkExceptionalHalt(final MessageFrame frame, final EVM evm) {

    final Operation op = frame.getCurrentOperation();
    if (frame.stackSize() + op.getStackSizeChange() > frame.getMaxStackSize()) {
      return Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
    }
    if (frame.stackSize() < op.getStackItemsConsumed()) {
      return Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    if (frame.getRemainingGas().compareTo(op.cost(frame)) < 0) {
      return Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    return op.exceptionalHaltCondition(frame, evm);
  }

  private Optional<Gas> calculateGasCost(final MessageFrame frame) {
    // Calculate the cost if, and only if, we are not halting as a result of a
    // stack underflow, as the operation may need all its stack items to
    // calculate gas. This is how existing EVM implementations behave.
    final Optional<ExceptionalHaltReason> exceptionalHaltReason =
        frame.getExceptionalHaltReason();
    if (exceptionalHaltReason.isEmpty() ||
        exceptionalHaltReason.get() !=
            ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS) {
      try {
        return Optional.ofNullable(frame.getCurrentOperation().cost(frame));
      } catch (final IllegalArgumentException e) {
        // TODO: Figure out a better way to handle gas overflows.
      }
    }
    return Optional.empty();
  }

  private void decrementRemainingGas(final MessageFrame frame,
                                     final Optional<Gas> currentGasCost) {
    frame.decrementRemainingGas(currentGasCost.orElseThrow(
        () -> new IllegalStateException("Gas overflow detected")));
  }

  private void checkForExceptionalHalt(final MessageFrame frame)
      throws ExceptionalHaltException {
    final Optional<ExceptionalHaltReason> exceptionalHaltReason =
        frame.getExceptionalHaltReason();
    if (exceptionalHaltReason.isPresent()) {
      LOG.trace("MessageFrame evaluation halted because of {}",
                exceptionalHaltReason);
      frame.setState(State.EXCEPTIONAL_HALT);
      frame.setOutputData(Bytes.EMPTY);
      throw new ExceptionalHaltException(exceptionalHaltReason.get());
    }
  }

  private void incrementProgramCounter(final MessageFrame frame) {
    final Operation operation = frame.getCurrentOperation();
    if (frame.getState() == State.CODE_EXECUTING &&
        !operation.getUpdatesProgramCounter()) {
      final int currentPC = frame.getPC();
      final int opSize = operation.getOpSize();
      frame.setPC(currentPC + opSize);
    }
  }

  private static void logState(final MessageFrame frame,
                               final Optional<Gas> currentGasCost) {
    if (LOG.isTraceEnabled()) {
      final StringBuilder builder = new StringBuilder();
      builder.append("Depth: ")
          .append(frame.getMessageStackDepth())
          .append("\n");
      builder.append("Operation: ")
          .append(frame.getCurrentOperation().getName())
          .append("\n");
      builder.append("PC: ").append(frame.getPC()).append("\n");
      currentGasCost.ifPresent(
          gas -> builder.append("Gas cost: ").append(gas).append("\n"));
      builder.append("Gas Remaining: ")
          .append(frame.getRemainingGas())
          .append("\n");
      builder.append("Depth: ")
          .append(frame.getMessageStackDepth())
          .append("\n");
      builder.append("Stack:");
      for (int i = 0; i < frame.stackSize(); ++i) {
        builder.append("\n\t").append(i).append(" ").append(
            frame.getStackItem(i));
      }
      LOG.trace(builder.toString());
    }
  }

  @VisibleForTesting
  Operation operationAtOffset(final Code code, final int contractAccountVersion,
                              final int offset) {
    final Bytes bytecode = code.getBytes();
    // If the length of the program code is shorter than the required offset,
    // halt execution.
    if (offset >= bytecode.size()) {
      return endOfScriptStop;
    }

    return operations.getOrDefault(bytecode.get(offset), contractAccountVersion,
                                   invalidOperation);
  }
}
