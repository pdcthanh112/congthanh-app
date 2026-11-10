module.exports = {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'", 'https://trusted.cdn.com'],
      imgSrc: ["'self'", 'data:', 'https://images.example.com'],
    },
  };
  