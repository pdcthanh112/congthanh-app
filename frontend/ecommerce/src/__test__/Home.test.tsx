import { render, screen } from '@testing-library/react';
import HomePage from '@/app/[locale]/home/page';

describe('Home', () => {
  it('should have Docs text', () => {
    render(<HomePage />); // ARRANGE

    const myElem = screen.getByText('Docs'); // ACT

    expect(myElem).toBeInTheDocument(); // ASSERT
  });

  it('should contain the text "information"', () => {
    render(<HomePage />); // ARRANGE

    const myElem = screen.getByText(/information/i); // ACT

    expect(myElem).toBeInTheDocument(); // ASSERT
  });

  it('should have a heading', () => {
    render(<HomePage />); // ARRANGE

    const myElem = screen.getByRole('heading', {
      name: 'Learn',
    }); // ACT

    expect(myElem).toBeInTheDocument(); // ASSERT
  });
});
