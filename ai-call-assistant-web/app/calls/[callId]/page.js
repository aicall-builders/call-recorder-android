import CallDetailClient from './CallDetailClient';

export function generateStaticParams() {
  return [{ callId: 'placeholder' }];
}

export default function Page() {
  return <CallDetailClient />;
}
