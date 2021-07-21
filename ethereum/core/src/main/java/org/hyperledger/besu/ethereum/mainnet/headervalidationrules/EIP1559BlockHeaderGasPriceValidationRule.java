/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.EIP1559MissingBaseFeeFromBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EIP1559BlockHeaderGasPriceValidationRule implements DetachedBlockHeaderValidationRule {
  private static final Logger LOG = LogManager.getLogger();
  private final EIP1559 eip1559;

  public EIP1559BlockHeaderGasPriceValidationRule(final EIP1559 eip1559) {
    this.eip1559 = eip1559;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    try {
      if (!eip1559.isEIP1559(header.getNumber())) {
        return true;
      }
      if (eip1559.isForkBlock(header.getNumber())) {
        return eip1559.getFeeMarket().getInitialBasefee()
            == header.getBaseFee().orElseThrow(EIP1559MissingBaseFeeFromBlockHeader::new);
      }

      final Long parentBaseFee =
          parent.getBaseFee().orElseThrow(EIP1559MissingBaseFeeFromBlockHeader::new);
      final Long currentBaseFee =
          header.getBaseFee().orElseThrow(EIP1559MissingBaseFeeFromBlockHeader::new);
      final long targetGasUsed = eip1559.targetGasUsed(parent);
      final long expectedBaseFee =
          eip1559.computeBaseFee(
              header.getNumber(), parentBaseFee, parent.getGasUsed(), targetGasUsed);
      if (expectedBaseFee != currentBaseFee) {
        LOG.info(
            "Invalid block header: basefee {} does not equal expected basefee {}",
            header.getBaseFee().orElseThrow(),
            expectedBaseFee);
        return false;
      }

      return true;
    } catch (final EIP1559MissingBaseFeeFromBlockHeader e) {
      LOG.info("Invalid block header: " + e.getMessage());
      return false;
    }
  }
}
