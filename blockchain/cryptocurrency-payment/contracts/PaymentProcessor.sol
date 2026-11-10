// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract PaymentProcessor is ReentrancyGuard, Ownable {
    mapping(address => bool) public acceptedTokens;
    mapping(uint256 => Payment) public payments;
    
    struct Payment {
        address payer;
        uint256 amount;
        uint256 timestamp;
        PaymentStatus status;
        address tokenAddress;
    }
    
    enum PaymentStatus { Pending, Completed, Refunded }
    
    event PaymentProcessed(
        uint256 indexed orderId,
        address indexed payer,
        uint256 amount,
        address tokenAddress
    );
    
    event PaymentRefunded(
        uint256 indexed orderId,
        address indexed payer,
        uint256 amount
    );
    
    constructor() {
        // Add default accepted tokens (e.g., USDT, USDC)
        acceptedTokens[address(0)] = true; // ETH
    }
    
    function addAcceptedToken(address tokenAddress) external onlyOwner {
        acceptedTokens[tokenAddress] = true;
    }
    
    function removeAcceptedToken(address tokenAddress) external onlyOwner {
        acceptedTokens[tokenAddress] = false;
    }
    
    function processPayment(
        uint256 orderId,
        uint256 amount,
        address tokenAddress
    ) external payable nonReentrant {
        require(acceptedTokens[tokenAddress], "Token not accepted");
        
        if (tokenAddress == address(0)) {
            require(msg.value == amount, "Incorrect ETH amount");
            
            payments[orderId] = Payment({
                payer: msg.sender,
                amount: amount,
                timestamp: block.timestamp,
                status: PaymentStatus.Completed,
                tokenAddress: address(0)
            });
            
            (bool success, ) = payable(owner()).call{value: amount}("");
            require(success, "ETH transfer failed");
        } else {
            IERC20 token = IERC20(tokenAddress);
            require(
                token.transferFrom(msg.sender, owner(), amount),
                "Token transfer failed"
            );
            
            payments[orderId] = Payment({
                payer: msg.sender,
                amount: amount,
                timestamp: block.timestamp,
                status: PaymentStatus.Completed,
                tokenAddress: tokenAddress
            });
        }
        
        emit PaymentProcessed(orderId, msg.sender, amount, tokenAddress);
    }
    
    function refundPayment(uint256 orderId) external onlyOwner nonReentrant {
        Payment storage payment = payments[orderId];
        require(payment.status == PaymentStatus.Completed, "Payment not completed");
        
        payment.status = PaymentStatus.Refunded;
        
        if (payment.tokenAddress == address(0)) {
            (bool success, ) = payable(payment.payer).call{value: payment.amount}("");
            require(success, "ETH refund failed");
        } else {
            IERC20 token = IERC20(payment.tokenAddress);
            require(
                token.transfer(payment.payer, payment.amount),
                "Token refund failed"
            );
        }
        
        emit PaymentRefunded(orderId, payment.payer, payment.amount);
    }
}