import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationPrevious,
  PaginationNext,
  PaginationEllipsis,
} from '@/components/ui/pagination';

describe('Pagination Component', () => {
  test('renders pagination correctly with pages', () => {
    render(
      <Pagination>
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious />
          </PaginationItem>
          <PaginationItem>
            <PaginationEllipsis />
          </PaginationItem>
          <PaginationItem>1</PaginationItem>
          <PaginationItem>2</PaginationItem>
          <PaginationItem>3</PaginationItem>
          <PaginationItem>
            <PaginationNext />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    );

    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByLabelText('Go to previous page')).toBeInTheDocument();
    expect(screen.getByLabelText('Go to next page')).toBeInTheDocument();
  });

  test('calls onPageChange when clicking next and previous', () => {
    const onPageChange = jest.fn();
    render(
      <Pagination>
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious onClick={() => onPageChange('prev')} />
          </PaginationItem>
          <PaginationItem>
            <PaginationNext onClick={() => onPageChange('next')} />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    );

    fireEvent.click(screen.getByLabelText('Go to next page'));
    fireEvent.click(screen.getByLabelText('Go to previous page'));

    expect(onPageChange).toHaveBeenCalledTimes(2);
    expect(onPageChange).toHaveBeenCalledWith('next');
    expect(onPageChange).toHaveBeenCalledWith('prev');
  });

  test('disables previous button on first page', () => {
    render(
      <Pagination>
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious isActive={false} />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    );

    expect(screen.getByLabelText('Go to previous page')).toBeDisabled();
  });

  test('disables next button on last page', () => {
    render(
      <Pagination>
        <PaginationContent>
          <PaginationItem>
            <PaginationNext isActive={false} />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    );

    expect(screen.getByLabelText('Go to next page')).toBeDisabled();
  });
});
