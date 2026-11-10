export type Response<T> = {
  data: T;
  message: string;
  statusCode: number;
};

export type ResponseWithPagination<T> = {
  data: {
    responseList: T[];
    paginationInfo: {
      page: number;
      limit: number;
      totalPage: number;
      totalElement: number;
    };
    totalPage: number;
  };
  message: string;
  statusCode: number;
};
