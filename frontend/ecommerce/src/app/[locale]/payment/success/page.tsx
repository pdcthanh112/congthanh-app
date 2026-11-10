'use client';

import { useSearchParams } from 'next/navigation';

export default function PaymentSuccess() {
    const searchParams = useSearchParams();

    const paymentId = searchParams.get('paymentId');
    const token = searchParams.get('token');
    const payerId = searchParams.get('PayerID');

  const handleExecutePaypalPayment = () => {
    
  }

    return (
        <div>
            <h1>Payment Success</h1>
            <p>Payment ID: {paymentId}</p>
            <p>Token: {token}</p>
            <p>Payer ID: {payerId}</p>
        </div>
    );
}
