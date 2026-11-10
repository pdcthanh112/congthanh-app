import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from '../button'

describe('Button Component', () => {
  it('renders button with correct text', async () => {
    const mockClick = jest.fn()
    render(<Button onClick={mockClick}>Click me</Button>)
    
    const user = userEvent.setup()
    await user.click(screen.getByText('Click me'))
    expect(screen.getByText('Click me')).toBeInTheDocument()
  })

  it('calls onClick when clicked', async () => {
    const mockClick = jest.fn()
    render(<Button onClick={mockClick}>Click me</Button>)

    const user = userEvent.setup()
    await user.click(screen.getByText('Click me'))
    user.click(screen.getByText('Click me'))
    expect(mockClick).toHaveBeenCalledTimes(1)
  })

  it('is disabled when disabled prop is true', () => {
    const mockClick = jest.fn()
    render(<Button onClick={mockClick} disabled>Click me</Button>)
    
    expect(screen.getByText('Click me')).toBeDisabled()
  })
})
