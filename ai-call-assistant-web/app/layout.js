import Script from "next/script";
import "./globals.css";

export const metadata = {
  title: "AI 통화 비서",
  description: "소상공인을 위한 AI 통화 요약 서비스",
};

export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <head>
        {/* Kakao JavaScript SDK v2: Kakao.Auth.authorize 기반 OAuth */}
        <Script
         src="https://t1.kakaocdn.net/kakao_js_sdk/2.7.2/kakao.min.js"
         integrity="sha384-TiCUE00h649CAMonG018J2ujOgDKW/kVWlChEuu4jK2vxfAAD0eZxzCKakxg55G4"
         crossOrigin="anonymous"
         strategy="beforeInteractive"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}