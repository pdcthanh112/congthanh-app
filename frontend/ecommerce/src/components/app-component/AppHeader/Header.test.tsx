import { render, screen } from '@testing-library/react';
import AppHeader from './AppHeader';

describe('Header', () => {
  it('shows up correctly', () => {
    render(<AppHeader />);
  });

  it('should render the "Next Todos" heading', () => {
    render(<AppHeader title="Next Todos" />); // ARRANGE

    //ACT
    const header = screen.getByRole('heading', {
      name: 'Next Todos',
    });

    expect(header).toBeInTheDocument(); // ASSERT
  });

  it('should render "Dave" as a heading', async () => {
    render(<AppHeader title="Dave" />); // ARRANGE

    //ACT
    const header = screen.getByRole('heading', {
      name: 'Dave',
    });

    expect(header).toBeInTheDocument(); // ASSERT
  });
});
