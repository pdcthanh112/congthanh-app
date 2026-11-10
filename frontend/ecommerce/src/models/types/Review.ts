export type Review = {
  id: number;
  content: string;
  rating: number;
  customerId: string;
  product: string;
  reviewMedia: ReviewMedia[];
  createdAt: Date
};

export type ReviewMedia = {
  id: number;
  url: string;
};

export type ReviewStatistic = {
  totalReview: number;
  averageRating: number;
  reviewRating5Star: number;
  reviewRating4Star: number;
  reviewRating3Star: number;
  reviewRating2Star: number;
  reviewRating1Star: number;
  reviewWithMedia: number;
};
