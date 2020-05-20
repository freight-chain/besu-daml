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
package org.hyperledger.besu.ethereum.privacy;

import java.util.Objects;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class PrivateTransactionWithMetadata {
  private final PrivateTransaction privateTransaction;
  private final PrivateTransactionMetadata privateTransactionMetadata;

  public static PrivateTransactionWithMetadata readFrom(final RLPInput input)
      throws RLPException {
    input.enterList();
    final PrivateTransaction privateTransaction =
        PrivateTransaction.readFrom(input.readAsRlp());
    final PrivateTransactionMetadata privateTransactionMetadata =
        PrivateTransactionMetadata.readFrom(input.readAsRlp());
    input.leaveList();
    return new PrivateTransactionWithMetadata(privateTransaction,
                                              privateTransactionMetadata);
  }

  public PrivateTransactionWithMetadata(
      final PrivateTransaction privateTransaction,
      final PrivateTransactionMetadata privateTransactionMetadata) {
    this.privateTransaction = privateTransaction;
    this.privateTransactionMetadata = privateTransactionMetadata;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();
    privateTransaction.writeTo(out);
    privateTransactionMetadata.writeTo(out);
    out.endList();
  }

  public PrivateTransaction getPrivateTransaction() {
    return privateTransaction;
  }

  public PrivateTransactionMetadata getPrivateTransactionMetadata() {
    return privateTransactionMetadata;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    final PrivateTransactionWithMetadata that =
        (PrivateTransactionWithMetadata)o;
    return privateTransaction.equals(that.privateTransaction) &&
        privateTransactionMetadata.equals(that.privateTransactionMetadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(privateTransaction, privateTransactionMetadata);
  }
}
