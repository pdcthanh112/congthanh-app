export type Voucher = {
  id: string;
  code: string;
  type: 'Discount' | 'Promotion';
  value: number;
  usageLimit: number;
  description: string;
  startDate: Date;
  endDate: Date;
  status: string;
};
