import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProductItemCard from '.';
import { Product } from '@/models/types';

const product: Product = {
  id: '123',
  name: 'Product 1',
  category: [],
  slug: 'product-slug',
  description: 'product-description',
  supplier: '',
  thumbnail: {
    id: 1,
    product: '',
    imagePath: '',
  },
  image: [{ id: 1, product: '', imagePath: '' }],
  parent: '',
  hasVariant: false,
  brand: 'brand',
  status: '',
  variant: [],
  attribute: [],
  metadata: {
    title: 'product-title',
    keywords: 'product-keyword',
    description: 'description',
  },
};

const addToCartMock = jest.fn();

describe('ProductCard', () => {
  it('renders product details correctly', () => {
    render(<ProductItemCard product={product} />);

    expect(screen.getByText(/Product 1/)).toBeInTheDocument();
    expect(screen.getByText('$100')).toBeInTheDocument();
    expect(screen.getByTestId('add-to-cart')).toBeInTheDocument();
  });
});

describe('Behavior', () => {
  it('calls addToCart when button is clicked', () => {
    render(<ProductItemCard product={product} />);

    fireEvent.click(screen.getByTestId('add-to-cart'));
    expect(addToCartMock).toHaveBeenCalledWith(product);
  });

  it('should call setTodos when checkbox clicked', async () => {
    render(<ProductItemCard todo={mockTodo} setTodos={mockSetTodos} />);

    const checkbox = screen.getByRole('checkbox');
    await userEvent.click(checkbox);

    expect(mockSetTodos).toBeCalled();
  });
});
