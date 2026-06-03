import StoreCallsClient from './StoreCallsClient';

export function generateStaticParams() {
  return [{ id: 'placeholder' }];
}

export default function Page() {
  return <StoreCallsClient />;
}
