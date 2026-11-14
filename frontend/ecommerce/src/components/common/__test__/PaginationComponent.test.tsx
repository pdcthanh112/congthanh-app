import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import PaginationComponent, { pageRange } from '../PaginationComponent';

vi.mock('next-intl', () => ({ useTranslations: () => (key: string) => key }));

describe('pageRange', () => {
  it('rút gọn bằng dấu …', () => {
    expect(pageRange(5, 10)).toEqual([1, '…', 4, 5, 6, '…', 10]);
    expect(pageRange(1, 3)).toEqual([1, 2, 3]);
  });
});

describe('PaginationComponent', () => {
  it('không hiện khi chỉ có một trang', () => {
    const { container } = render(<PaginationComponent page={1} totalPage={1} onChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('gọi onChange và chặn nút trước ở trang đầu', () => {
    const onChange = vi.fn();
    render(<PaginationComponent page={1} totalPage={3} onChange={onChange} />);
    expect(screen.getByLabelText('previous_page')).toBeDisabled();
    fireEvent.click(screen.getByText('2'));
    fireEvent.click(screen.getByLabelText('next_page'));
    expect(onChange.mock.calls).toEqual([[2], [2]]);
  });
});
