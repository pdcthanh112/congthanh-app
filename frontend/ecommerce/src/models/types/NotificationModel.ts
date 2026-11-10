export type Notification = {
  id: number;
  customer: string;
  title: string;
  content: string;
  url: string;
  image: string;
  createdAt: Date | string;
  isRead: boolean;
  status: string;
};
