import axiosInstance from '@/config/axiosConfig';

export const executePaypalPayment = async (paymentId: string, payerId: string) => {
  await axiosInstance
    .post('/payment/paypal/execute', {
      paymentId,
      payerId,
    })
    .then((response) => response.data);
};

export const addPaymentCard = async () => {
  return await axiosInstance.post('/payments/card').then((response) => response.data);
};
