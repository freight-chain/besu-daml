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
 *
 */

package org.hyperledger.besu.ethereum.mainnet.precompiles;

import java.util.function.Function;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.PrecompileContractRegistry;
import org.hyperledger.besu.ethereum.mainnet.PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.PrecompiledContractConfiguration;

public class AbstractPrecompiledContractTest {
  protected final PrecompiledContract contract;

  public AbstractPrecompiledContractTest(
      final Function<PrecompiledContractConfiguration,
                     PrecompileContractRegistry> registryFactory,
      final Address precompiledAddress) {
    contract = registryFactory
                   .apply(new PrecompiledContractConfiguration(
                       new IstanbulGasCalculator(), PrivacyParameters.DEFAULT))
                   .get(precompiledAddress, Account.DEFAULT_VERSION);
  }
}
