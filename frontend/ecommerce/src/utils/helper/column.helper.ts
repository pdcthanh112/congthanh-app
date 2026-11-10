/* eslint-disable @typescript-eslint/no-explicit-any */
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";

export const ColumnHelper = {
  getColumnProductManagement: () => {
    const columnHelper = createColumnHelper<any>();
    const columns: ColumnDef<any, any>[] = [
      columnHelper.accessor("name", {
        header: "Name",
        cell: (info) => info.getValue(),
      }),
    ];
    return columns;
  },
};
