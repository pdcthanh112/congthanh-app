'use client';
import { Autocomplete, TextField } from '@mui/material';
import Image from 'next/image';

export default function CancelPaymentPage() {
  const reason = [
    { id: 1, value: 'Customer reasons:' },
    { id: 2, value: 'Changed mind' },
    // 'Found a better deal',
    // 'Incorrect information entered ',
    // 'No longer needed ',
    // "Buyer's remorse ",
    // 'Duplicate order ',
    // 'Product issues:',
    // 'Out of stock ',
    // 'Damaged product ',
    // 'Incorrect item shipped ',
    // 'Product unavailable ',
    // 'Shipping issues:',
    // 'High shipping cost ',
    // 'Long delivery time ',
    // 'Delivery address problem ',
    // 'Shipping delays ',
    // 'Payment issues:',
    // 'Payment failure ',
    // 'Billing error ',
    // 'Technical issues:',
    // 'Website error ',
    // 'System glitch ',
    // 'Seller issues:',
    // 'Unresponsive seller ',
    // 'Supplier issues ',
    // 'Order processing error',
  ];
  return (
    <div className="w-4/5 mx-auto bg-white gap-4">
      <h3>Request Cancellation</h3>
      <div className="grid grid-cols-12">
        <div className="col-span-6">
          Item
          <span className="relative w-20 h-20">
            <Image
              src={'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQk-DEEtycr_JgnwVmW3k_rwqiKt7axa9S93A&s'}
              alt=""
              objectFit="fit"
              fill
            />
          </span>
          <span>Iphone 16 pro max</span>
        </div>
        <div className="col-span-2">Quantity: 2</div>
        <div className="col-span-4">
          Reason
          <Autocomplete
            options={reason}
            size="small"
            sx={{ width: '100%' }}
            getOptionLabel={(option) => option.value}
            renderInput={(params) => <TextField {...params} label="" />}
          />
        </div>
      </div>
      <div>
        Addition information
        <TextField multiline minRows={2} maxRows={4} sx={{ width: '100%' }} />
      </div>
      <div>Policy</div>
    </div>
  );
}
