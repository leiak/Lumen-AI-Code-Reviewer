/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        council: { 50: '#f0f9ff', 500: '#3b82f6', 700: '#1d4ed8', 900: '#1e3a8a' },
        critical: '#dc2626',
        major: '#f59e0b',
        minor: '#10b981',
      },
    },
  },
  plugins: [],
};
