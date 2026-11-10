// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract LoyaltyToken is ERC20 {
    address public owner;

    constructor() ERC20("LoyaltyToken", "LTK") {
        owner = msg.sender;
        _mint(owner, 1000000 * 10 ** decimals()); // Tạo 1 triệu token ban đầu
    }

    modifier onlyOwner() {
        require(msg.sender == owner, "Not authorized");
        _;
    }

    function rewardCustomer(address customer, uint256 amount) public onlyOwner {
        _transfer(owner, customer, amount);
    }

    function redeemPoints(address customer, uint256 amount) public onlyOwner {
        _transfer(customer, owner, amount);
    }
}
