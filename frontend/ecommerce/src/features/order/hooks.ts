// import { getOrderByStatus } from '@/api/orderApi';
// import { useQuery } from '@tanstack/react-query';

// export const useGetOrder = () => {
//   const { data: listOrder, isLoading } = useQuery({
//     queryKey: ['order', status],
//     queryFn: async () =>
//       await getOrderByStatus(status, pagination.page - 1, pagination.limit).then((result) => {
//         setPagination({ ...pagination, totalPage: result.data.totalPage });
//         return result.data.responseList;
//       }),
//   });
//   return { listOrder, isLoading };
// };
