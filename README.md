<div align="center">

# Privacy Preserving Smart Contract Energy Trading

[Example Contract Repository](https://gitlab.com/partisiablockchain/language/example-contracts)
| [PBC Documentation](https://partisiablockchain.gitlab.io/documentation/)

</div>

Smart Contracts are written in the [Rust programming
language](https://rust-lang.org/), using the [Paritisa Blockchain Contract
SDK](https://gitlab.com/partisiablockchain/language/contract-sdk/). Tests of contract
functionality is written in Java, and uses the
[JUnit contract testing framework](https://gitlab.com/partisiablockchain/language/junit-contract-test/).

## Contracts Overview

Contracts of this repository demonstrate the four following feature sets:

- **Basic Smart Contracts**: These demonstrate how to [develop a standard smart
  contract](https://partisiablockchain.gitlab.io/documentation/smart-contracts/introduction-to-smart-contracts.html)
  in Rust for deployment to Partisia Blockchain.
- **ZK (Multi-Party Computation)**: These demonstrate how to use Partisia
  Blockchain's unique [Multi-party
  computations](https://partisiablockchain.gitlab.io/documentation/smart-contracts/zk-smart-contracts/zk-smart-contracts.html)
  capability to enhance privacy on Web3, while retaining full auditability.

The **Basic** contracts are:

- [`double-auction`](./rust/double-auction): Implements a double auction
  mechanism, where users can buy and sell energy. The contract is based
  on the original [ZK double auction](https://github.com/Fontex5/Thesis-Project-MPC-Energy-Trading) 
  by Mohammad Esfahaniasl.

The **ZK (Multi-Party Computation)** contracts are:

- [`zk-double-auction`](./rust/zk-double-auction): [ZK double auction](https://github.com/Fontex5/Thesis-Project-MPC-Energy-Trading) 
  by Mohammad Esfahaniasl.
- [`optimized-zk-double-auction`](./rust/optimized-zk-double-auction): Implements the same
  double auction mechanism as the `zk-double-auction` contract, but with
  optimizations to reduce gas costs, as well as other improvements such as readability.

## Usage

All smart contracts can be compiled using the [Cargo Partisia Contract](https://gitlab.com/partisiablockchain/language/cargo-partisia-contract) tool:

First, ensure you have the `Rust`, `Java`, `Python` and `Maven` installed on your machine, or
run the folder with the included [`devcontainer.json`](./.devcontainer/devcontainer.json).

Next, install the toolchain:
```bash
rustup install stable
rustup default 1.86 
```
As well as the `wasm32-unknown-unknown` target:
```bash
cd rust 
rustup target add wasm32-unknown-unknown 
cargo install cargo-partisia-contract
```
Then, build the contracts (still in the `rust` directory):
```bash
cargo pbc build --release
```

The `--release` argument ensures that contracts are minimized. Resulting
contract `.pbc` files can be found in the `rust/target/wasm32-unknown-unknown/release` folder, and can be
 [directly deployed to Partisia Blockchain](https://partisiablockchain.gitlab.io/documentation/smart-contracts/compile-and-deploy-contracts.html).

Individual contracts can be compiled directly from the respective contract's
directory.

## Testing

### Unit testing

The smart contract test suite can be run by the following script:

```bash
cd contract-java-test 
mvn test
```

### Complexity testing

[`ComplexityTool.java`](./contract-java-test/src/test/java/examples/ComplexityTool.java) uses the Partisia Blockchain Complexity Tool to get the number of multiplications and
rounds, done by the ZK nodes. It can be run with the following command:

```bash
cd contract-java-test 
mvn test -Dtest=ComplexityTool
```

### Testing on the Partisia Blockchain Testnet

To test the contracts on the Partisia Blockchain Testnet, we have created a Python script that uploads the
compiled contracts to the testnet and adds random transactions to the contract, before holding the double
auction.

The script can be found in the [`scripts`](./scripts/) directory. To run the script, you need to have Python 3.x installed.

Run the script with the following command, to test the [`optimized-zk-double-auction`](./scripts/testnet-optimized-zk-double-auction.py) contract:

```bash
cd scripts 
python3 testnet-optimized-zk-double-auction.py
```
