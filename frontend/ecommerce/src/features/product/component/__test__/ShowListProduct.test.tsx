import { render, screen } from '@testing-library/react';
import ShowListProduct from '../ShowListProduct';
import { Product } from '@/models/types';

const dummyData: Product[] = [
  {
    id: 'lkaslfjldsfjaslks',
    name: 'AAAAAAAAAAAAA',
    category: [1412423],
    slug: 'adfasddf',
    description: 'adladsjljladsf',
    supplier: 'string',
    image: [
      {
        id: 1,
        product: 'lkaslfjldsfjaslks',
        imagePath:
          'https://images.stockcake.com/public/7/3/7/7375ee9e-ecee-484f-9f33-7a9a55d079ce_large/sunny-daisy-field-stockcake.jpg',
      },
      {
        id: 2,
        product: 'lkaslfjldsfjaslks',
        imagePath:
          'https://images.stockcake.com/public/7/3/7/7375ee9e-ecee-484f-9f33-7a9a55d079ce_large/sunny-daisy-field-stockcake.jpg',
      },
      {
        id: 3,
        product: 'lkaslfjldsfjaslks',
        imagePath:
          'https://images.stockcake.com/public/7/3/7/7375ee9e-ecee-484f-9f33-7a9a55d079ce_large/sunny-daisy-field-stockcake.jpg',
      },
    ],
    brand: 'string',
    status: 'string',
    variant: [],
    attribute: [],
  },
];

const mockSetTodos = jest.fn();

describe('ShowListProduct', () => {
  it('should render "No Product Available" when the array is empty', () => {
    render(<ShowListProduct listProduct={[]} loading={false} />);

    const message = screen.getByText('No Product Available');

    expect(message).toBeInTheDocument();
  });

  it('should render a list with the correct number of items', () => {
    render(<ShowListProduct listProduct={dummyData} setTodos={mockSetTodos} loading={false} />);

    const todosArray = screen.getAllByRole('article');

    expect(todosArray.length).toBe(2);
  });
});
