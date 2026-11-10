import React from 'react';
import { getOrderDetail } from '@/api/orderApi';
import { Steps } from 'antd';
import Link from 'next/link';
import { PackageCheck, PackageOpen, Truck, CheckCircle, XCircle, RotateCcw, AlertCircle, Clock } from 'lucide-react';
import { OrderStatus } from '@/utils/constants/enum';
import { useSession } from 'next-auth/react';
import { getServerSession } from 'next-auth';

const orderStatusIconMap: Record<OrderStatus, React.ReactElement> = {
  [OrderStatus.PLACED]: <PackageOpen className="text-blue-500" />,
  [OrderStatus.PREPARING]: <PackageCheck className="text-yellow-500" />,
  [OrderStatus.SHIPPING]: <Truck className="text-orange-500" />,
  [OrderStatus.DELIVERED]: <CheckCircle className="text-green-500" />,
  [OrderStatus.CANCELLED]: <XCircle className="text-red-500" />,
  [OrderStatus.RETURNED]: <RotateCcw className="text-purple-500" />,
  [OrderStatus.FAILED]: <AlertCircle className="text-gray-500" />,
  [OrderStatus.COMPLETED]: <CheckCircle className="text-green-500" />,
};

export default async function OrderDetailPage({ params }: { params: { orderCode: string } }) {
  const { orderCode } = params;

  const session = await getServerSession()
console.log("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAa",session)
  // const orderDetail =  await getOrderDetail(orderCode).then((result) => result);
  const orderDetail = {
    id: 1,
    customer: 'John Doe',
    total: 1000000,
    note: 'Please deliver fast',
    orderDate: 1633072800000,
    status: 'SHIPPED',
    orderCode: 'ORD12345',
    orderItems: [
      {
        id: 1,
        quantity: 2,
        product: { id: 1, name: 'Product A', price: 50 },
        status: 'SHIPPED',
      },
    ],
    shippingAddress: '123 Main St, City, Country',
    statusTracking: [
      {
        id: 1,
        status: OrderStatus.COMPLETED,
        description: 'Order has been shipped',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.SHIPPING,
        description: 'Order has been shipped',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.SHIPPING,
        description:
          'Đơn hàng đã đến trạm giao hàng tại khu vực của bạn Phường 13, Quận Bình Thạnh, TP. Hồ Chí Minh và sẽ được giao trong vòng 24 giờ tiếp theo',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.SHIPPING,
        description: 'Đơn hàng đã rời kho phân loại tới 50-HCM Binh Thanh Hub',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.SHIPPING,
        description: 'Đơn hàng đã đến kho phân loại Xã Tân Phú Trung, Huyện Củ Chi, TP. Hồ Chí Minh',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.SHIPPING,
        description: 'Đơn hàng đã rời bưu cục',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.SHIPPING,
        description: 'Đơn hàng đã đến bưu cục Phường Đông Hưng Thuận, Quận 12, TP. Hồ Chí Minh',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.PREPARING,
        description: 'Order has been shipped',
        changedAt: new Date(),
      },
      {
        id: 1,
        status: OrderStatus.PLACED,
        description: 'Order has been shipped',
        changedAt: new Date(),
      },
    ],
  };

  const orderStep: {
    title: string;
    description: string;
    status: 'finish' | 'process' | 'wait' | 'error' | undefined;
  }[] = [
    { title: 'Đơn hàng được tạo thành công', description: new Date().toDateString(), status: 'finish' },
    { title: 'Đơn hàng được xác nhận', description: new Date().toDateString(), status: 'finish' },
    { title: 'Đơn hàng đang được chuẩn bị', description: new Date().toDateString(), status: 'finish' },
    { title: 'Đơn hàng đã đưa cho đơn vị vận chuyển', description: new Date().toDateString(), status: 'finish' },
    { title: 'Hàng hoá đang được vận chuyển', description: new Date().toDateString(), status: 'process' },
    { title: 'Hàng hoá đang được vận chuyển', description: new Date().toDateString(), status: 'process' },
    { title: 'Đơn hàng được giao thành công', description: new Date().toDateString(), status: 'wait' },
  ];

  const trackingItems = orderDetail.statusTracking.map((item) => ({
    title: item.status,
    description: item.description,
    subTitle: item.changedAt.toLocaleDateString(),
    icon: orderStatusIconMap[item.status as OrderStatus],
    // status: item.status === "SHIPPED" ? "process" : "wait",
  }));

  return (
    <React.Fragment>
      <div className="bg-white w-4/5 mx-auto p-2">
        <div className="flex justify-between my-3">
          <Link href="/order" className="hover:underline">
            Back to Orders
          </Link>
          <h3>
            Order Code: <span className="opacity-80">{orderDetail.orderCode}</span>
          </h3>
        </div>

        <Steps items={orderStep} labelPlacement="vertical" className='font-semibold'/>
        <div className="min-h-[30rem]">
          <Steps items={trackingItems} direction="vertical" size="small" className='font-medium'/>
        </div>
      </div>
    </React.Fragment>
  );
}
