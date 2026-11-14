import 'server-only';
import sanitizeHtml from 'sanitize-html';

/**
 * Làm sạch HTML do người bán nhập (mô tả sản phẩm) trước khi render bằng dangerouslySetInnerHTML:
 * chỉ giữ thẻ định dạng an toàn, bỏ script/style/iframe, thuộc tính on*, URL javascript:/data: ở link.
 */
export const sanitizeRichText = (html: string | null | undefined): string =>
  sanitizeHtml(html ?? '', {
    allowedTags: [
      ...sanitizeHtml.defaults.allowedTags.filter((tag) => tag !== 'iframe'),
      'img',
      'figure',
      'figcaption',
    ],
    allowedAttributes: {
      a: ['href', 'name', 'target', 'rel', 'title'],
      img: ['src', 'alt', 'title', 'width', 'height', 'loading'],
      '*': ['class'],
    },
    allowedSchemes: ['http', 'https', 'mailto'],
    allowedSchemesByTag: { img: ['http', 'https'] },
    transformTags: {
      a: sanitizeHtml.simpleTransform('a', { rel: 'noopener noreferrer nofollow', target: '_blank' }),
      img: sanitizeHtml.simpleTransform('img', { loading: 'lazy' }),
    },
  });
