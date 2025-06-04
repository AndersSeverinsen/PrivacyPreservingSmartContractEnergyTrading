import os, random, time

MIN = 0
MAX = 0
JUMP = 5
REPETITIONS = 2
VERBOSE = False

FILE_NAME = "zk_double_auction"
PATH = f"../rust/target/wasm32-unknown-unknown/release/"

for num_of in range(MIN, MAX+1, JUMP):
    for repetition in range(REPETITIONS):
        NUM_OF_SELL_ORDERS = num_of
        NUM_OF_BUY_ORDERS = num_of

        account_create = os.popen('cargo pbc account create').read()
        if VERBOSE:
            print(account_create)
        private_key = account_create.split()[6] + ".pk"

        command = f'cargo pbc transaction deploy --abi={PATH}{FILE_NAME}.abi --gas=2500000 {PATH}{FILE_NAME}.zkwa --privatekey={private_key}'

        command_output = os.popen(command).read()
        if VERBOSE:
            print(command_output)
        else:
            link = command_output.split()[12]
            print(f"---")
            print(f"{link}")
            print(f"{NUM_OF_SELL_ORDERS}\t{NUM_OF_BUY_ORDERS}\t{NUM_OF_SELL_ORDERS+NUM_OF_BUY_ORDERS}")

        time.sleep(5)

        contract_address = command_output.split()[6]

        if VERBOSE:
            print("Updating prices.\n")
        min = 0
        max = 100
        command = f'cargo pbc transaction action {contract_address} update_prices {min} {max} --gas 60000 --privatekey={private_key}'
        if VERBOSE:
            print(command)
        os.popen(command)
        time.sleep(5)

        if VERBOSE:
            print("Adding sell and buy orders.\n")

        for i in range(NUM_OF_SELL_ORDERS):
            random_numbers = [random.randint(0, 100) for _ in range(6)]

            # Make sure the input is aggregated
            random_numbers.sort()

            secret_order_struct = "{ " + f'{i} [ {" ".join(map(str, random_numbers))} ]' + " }"
            command = f'cargo pbc transaction action {contract_address} secret_input_sell_order {secret_order_struct} --gas 60000 --privatekey={private_key}'

            if VERBOSE:
                print(command)
            else:
                print(".", end="")

            os.popen(command)

            time.sleep(5)

        print()
        for i in range(NUM_OF_BUY_ORDERS):
            random_numbers = [random.randint(0, 100) for _ in range(6)]

            # Make sure the input is aggregated
            random_numbers.sort()
            random_numbers.reverse()

            secret_order_struct = "{ " + f'{i+NUM_OF_SELL_ORDERS} [ {" ".join(map(str, random_numbers))} ]' + " }"
            command = f'cargo pbc transaction action {contract_address} secret_input_buy_order {secret_order_struct} --gas 60000 --privatekey={private_key}'

            if VERBOSE:
                print(command)
            else:
                print(".", end="")

            os.popen(command)

            time.sleep(5)

        print()
        time.sleep(15)
        # Pre transaction balance
        output = os.popen(f'cargo pbc contract show --balance {contract_address}').read()
        if VERBOSE:
            print(output)
        import re
        match1 = re.search(r'"value"\s*:\s*"(\d+)"', output)
        value1 = match1.group(1)
        value2 = None
        while value1 != value2:
            value2 = value1
            output = os.popen(f'cargo pbc contract show --balance {contract_address}').read()
            match1 = re.search(r'"value"\s*:\s*"(\d+)"', output)
            value1 = match1.group(1)
            time.sleep(5)
        if VERBOSE:
            print(output)


        # Hold double auction
        output = os.popen(f'cargo pbc transaction action {contract_address} hold_double_auction --gas 500000 --privatekey={private_key}').read()
        
        if VERBOSE:
            print(output)

        time.sleep(30)

        # Post transaction balance
        output = os.popen(f'cargo pbc contract show --balance {contract_address}').read()
        match2 = re.search(r'"value"\s*:\s*"(\d+)"', output)

        value1 = match2.group(1)
        value2 = None
        while value1 != value2:
            value2 = value1
            output = os.popen(f'cargo pbc contract show --balance {contract_address}').read()
            match2 = re.search(r'"value"\s*:\s*"(\d+)"', output)
            value1 = match1.group(1)
            time.sleep(5)
        if VERBOSE:
            print(output)

        if VERBOSE:
            print(match1.group(1), "\t", match2.group(1))
        else:
            print(f"{match1.group(1)}\t{match2.group(1)}\t{int(match1.group(1)) - int(match2.group(1))+500000}")
            print(f"---")
