import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Product } from '@/models/types';
import ShowListProduct from '../ShowListProduct';

vi.mock('next-intl', () => ({ useTranslations: () => (key: string) => key }));
vi.mock('../ProductItemCard', () => ({
  default: ({ product }: { product: Product }) => <article>{product.name}</article>,
}));

const product = (id: string) => ({ id, name: `Sản phẩm ${id}`, slug: id }) as Product;

describe('ShowListProduct', () => {
  it('hiện thông báo khi danh sách rỗng', () => {
    render(<ShowListProduct listProduct={[]} />);
    expect(screen.getByText('product.empty')).toBeInTheDocument();
  });

  it('hiện đúng số thẻ sản phẩm', () => {
    render(<ShowListProduct listProduct={[product('1'), product('2')]} />);
    expect(screen.getAllByRole('article')).toHaveLength(2);
  });

  it('hiện skeleton khi đang tải', () => {
    const { container } = render(<ShowListProduct loading skeletonCount={3} />);
    expect(container.querySelector('[aria-busy]')?.children).toHaveLength(3);
  });
});
