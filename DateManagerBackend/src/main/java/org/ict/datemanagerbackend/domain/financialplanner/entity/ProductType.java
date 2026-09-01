package org.ict.datemanagerbackend.domain.financialplanner.entity;

// finlife API가 예금(depositProductsSearch)과 적금(savingProductsSearch)을 별도 엔드포인트로
// 나눠서 주기 때문에, 캐시 테이블에서도 이 값으로 구분해야 같은 fin_prdt_cd가 섞이지 않는다.
public enum ProductType {
  DEPOSIT, // 정기예금
  SAVING   // 적금
}
