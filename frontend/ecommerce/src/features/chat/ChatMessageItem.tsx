import { cn } from '@/lib/utils';
import { useTranslations } from 'next-intl';
import ChatProductCard from './ChatProductCard';
import type { ChatMessage, ChatTool } from './hooks';
import MessageText from './MessageText';

const TOOL_LABEL_KEYS: Record<string, string> = {
  search_products: 'tool_search',
  get_product: 'tool_product',
  recommend_products: 'tool_recommend',
  view_cart: 'tool_cart',
  add_to_cart: 'tool_cart',
  update_cart_item: 'tool_cart',
  list_orders: 'tool_orders',
  get_order: 'tool_orders',
};

const ERROR_KEYS: Record<string, string> = { RATE_LIMITED: 'error_rate_limited' };

function runningTool(tools: ChatTool[]): ChatTool | undefined {
  return tools.find((tool) => tool.ok === undefined);
}

export default function ChatMessageItem({ message, streaming }: { message: ChatMessage; streaming: boolean }) {
  const t = useTranslations('chat');
  const isUser = message.role === 'user';
  const running = streaming ? runningTool(message.tools) : undefined;
  const thinking = streaming && !message.text && !running;

  return (
    <li className={cn('flex flex-col gap-2', isUser ? 'items-end' : 'items-start')}>
      {(message.text || isUser) && (
        <div
          className={cn(
            'max-w-[85%] rounded-lg px-3 py-2 text-sm',
            isUser ? 'bg-yellow-400 text-black' : 'bg-gray-100'
          )}
        >
          <MessageText text={message.text} />
        </div>
      )}
      {running && (
        <p className="text-xs text-gray-500" role="status">
          {t(TOOL_LABEL_KEYS[running.name] ?? 'thinking')}
        </p>
      )}
      {thinking && (
        <p className="text-xs text-gray-500" role="status">
          {t('thinking')}
        </p>
      )}
      {message.products.length > 0 && (
        <div className="flex w-full gap-2 overflow-x-auto pb-1">
          {message.products.map((p) => (
            <ChatProductCard key={p.id} product={p} />
          ))}
        </div>
      )}
      {message.error && (
        <p className="text-xs text-red-600" role="alert">
          {t(ERROR_KEYS[message.error] ?? 'error_generic')}
        </p>
      )}
    </li>
  );
}
