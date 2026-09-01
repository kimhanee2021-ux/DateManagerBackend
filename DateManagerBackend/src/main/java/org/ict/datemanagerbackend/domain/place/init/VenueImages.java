package org.ict.datemanagerbackend.domain.place.init;

import java.util.Map;

// 공연장(venue_name, 정확히 일치) -> 건물 대표사진 URL(위키미디어, 2026-08-28). Place.imageUrl(공연
// 포스터)과는 별개로 venueImageUrl에 채워서, "이 공연장의 다른 공연" 그룹핑 화면에서 공연장 건물
// 사진을 보여주는 용도로 쓴다 - MuseumSyncServiceImpl.NAME_IMAGES/SportsSyncServiceImpl.STADIUM_IMAGES와
// 같은 접근이다.
//
// KOPIS venue_name(fcltynm) 820개를 전부 위키백과에 일괄 조회해서 나온 57건 중, 사진이 실제로 그
// 공연장 건물이 아닌 것(예: "국립과천과학관"에 열차 사진, "유기체"/"온쉼표"/"신도시"처럼 이름만
// 우연히 일치하는 무관한 문서, "기장군청"/"남양주시청"/"춘천시청"/"아트센터 인천"처럼 확실하지 않은
// 매칭)은 제외하고 확실한 것만 남겼다.
public final class VenueImages {

  private VenueImages() {
  }

  public static final Map<String, String> IMAGES = Map.ofEntries(
      Map.entry("강동아트센터", "https://upload.wikimedia.org/wikipedia/commons/4/48/Gangdong_Art_Center.jpg"),
      Map.entry("강원대학교 백령아트센터", "https://upload.wikimedia.org/wikipedia/commons/e/e8/%EB%B0%B1%EB%A0%B9%EC%95%84%ED%8A%B8%EC%84%BC%ED%84%B0.jpg"),
      Map.entry("고양종합운동장", "https://upload.wikimedia.org/wikipedia/commons/e/e1/Goyang_sta_1.JPG"),
      Map.entry("과천시민회관", "https://upload.wikimedia.org/wikipedia/commons/a/ad/2015%EB%85%84_6%EC%9B%94_8%EC%9D%BC_%EA%B3%BC%EC%B2%9C%EC%8B%9C%EB%AF%BC%ED%9A%8C%EA%B4%80_DSC00645.jpg"),
      Map.entry("광주여자대학교", "https://upload.wikimedia.org/wikipedia/commons/f/fd/Gwangju_Women%27s_University.jpg"),
      Map.entry("국립국악원", "https://upload.wikimedia.org/wikipedia/commons/1/13/National_Gugak_Center_in_Seocho-gu%2C_Seoul%2C_South_Korea.jpg"),
      Map.entry("국립대구박물관", "https://upload.wikimedia.org/wikipedia/commons/7/77/Daegu_National_Museum.jpg"),
      Map.entry("국립부여박물관", "https://upload.wikimedia.org/wikipedia/commons/3/37/Buyeo_National_Museum_%2820160719_1%29.png"),
      Map.entry("국립중앙박물관", "https://upload.wikimedia.org/wikipedia/commons/2/24/National_Museum_of_Korea%2C_Seoul_%282%29_%2840236586235%29.jpg"),
      Map.entry("국립춘천박물관", "https://upload.wikimedia.org/wikipedia/commons/e/e3/%EA%B5%AD%EB%A6%BD%EC%B6%98%EC%B2%9C%EB%B0%95%EB%AC%BC%EA%B4%80_%EC%A0%95%EB%A9%B4.jpg"),
      Map.entry("국립현대미술관 서울관", "https://upload.wikimedia.org/wikipedia/commons/a/a9/MMCA_Seoul.jpg"),
      Map.entry("국립민속국악원", "https://upload.wikimedia.org/wikipedia/commons/2/2f/Namwon_National_Gugak_Center.JPG"),
      Map.entry("그랜드하얏트서울", "https://upload.wikimedia.org/wikipedia/commons/7/72/Grand_Hyatt_Seoul_exterior.jpg"),
      Map.entry("김해문화의전당", "https://upload.wikimedia.org/wikipedia/commons/9/94/Gimhae-GASC.png"),
      Map.entry("남동소래아트홀", "https://upload.wikimedia.org/wikipedia/commons/0/0e/%EB%82%A8%EB%8F%99%EC%86%8C%EB%9E%98%EC%95%84%ED%8A%B8%ED%99%80.jpg"),
      Map.entry("노들섬", "https://upload.wikimedia.org/wikipedia/commons/1/18/Nodeulseom_%2814005438206%29.jpg"),
      Map.entry("대구스타디움", "https://upload.wikimedia.org/wikipedia/commons/d/da/Daegu.Stadium.original.2167.jpg"),
      Map.entry("대구콘서트하우스", "https://upload.wikimedia.org/wikipedia/commons/3/3f/Daegu_Citizen_Centre.jpg"),
      Map.entry("대전시립미술관", "https://upload.wikimedia.org/wikipedia/commons/2/20/Daejeon_Museum_of_Art.jpg"),
      Map.entry("대전예술의전당", "https://upload.wikimedia.org/wikipedia/commons/c/c8/59254-Daejon.jpg"),
      Map.entry("대한성공회 서울주교좌성당", "https://upload.wikimedia.org/wikipedia/commons/b/bc/Seoul_Cathedral_Anglican_Church-8.jpg"),
      Map.entry("인천아트플랫폼", "https://upload.wikimedia.org/wikipedia/commons/6/6c/Incheon_Art_Flatform_191124001.jpg"),
      Map.entry("인천문화예술회관", "https://upload.wikimedia.org/wikipedia/commons/4/48/%EC%9D%B8%EC%B2%9C%EA%B4%91%EC%97%AD%EC%8B%9C_%EB%AC%B8%ED%99%94%EC%98%88%EC%88%A0%ED%9A%8C%EA%B4%80_2026%EB%85%84_1%EC%9B%94%EA%B2%BD.jpg"),
      Map.entry("잠실종합운동장", "https://upload.wikimedia.org/wikipedia/commons/0/04/Seoul_Sports_Complex.jpg"),
      Map.entry("전북도립미술관", "https://upload.wikimedia.org/wikipedia/commons/b/b5/Jeonbuk_Museum_of_Art%2C_in_Wanju%2C_North_Jeolla_Province%2C_South_Korea_07.jpg"),
      Map.entry("종로구립 박노수미술관", "https://upload.wikimedia.org/wikipedia/commons/6/68/Park_No-su_Art_Museum.jpg"),
      Map.entry("창원과학체험관", "https://upload.wikimedia.org/wikipedia/ko/2/2f/Changwon_science.jpg"),
      Map.entry("청주예술의전당", "https://upload.wikimedia.org/wikipedia/commons/7/77/%EC%B2%AD%EC%A3%BC%EC%98%88%EC%88%A0%EC%9D%98%EC%A0%84%EB%8B%B9.jpg"),
      Map.entry("킨텍스", "https://upload.wikimedia.org/wikipedia/commons/9/9d/%ED%82%A8%ED%85%8D%EC%8A%A4.jpg"),
      Map.entry("클레이아크 김해미술관", "https://upload.wikimedia.org/wikipedia/commons/a/a7/Clayarch_Gimhae_Museum.JPG"),
      Map.entry("목포문화예술회관", "https://upload.wikimedia.org/wikipedia/ko/9/9a/Mokpo_Art_Center.JPG"),
      Map.entry("명동대성당", "https://upload.wikimedia.org/wikipedia/commons/9/93/Myeongdongchurch2025.jpg"),
      Map.entry("문화비축기지", "https://upload.wikimedia.org/wikipedia/commons/a/a5/Mapo_Park_-_Human_Nature.jpg"),
      Map.entry("부산시민회관", "https://upload.wikimedia.org/wikipedia/ko/7/79/Busan_Citizens%27_Hall_%281%29.jpg"),
      Map.entry("부산해양자연사박물관", "https://upload.wikimedia.org/wikipedia/commons/8/8e/Korea-Busan_Marine_Natural_History_Museum-Entrance-01.jpg"),
      Map.entry("삼척문화예술회관", "https://upload.wikimedia.org/wikipedia/commons/3/35/Samcheok_Culture_and_Art_Center.jpg"),
      Map.entry("서소문성지 역사박물관", "https://upload.wikimedia.org/wikipedia/commons/8/86/%EC%84%9C%EC%86%8C%EB%AC%B8%EC%84%B1%EC%A7%80_%EC%97%AD%EC%82%AC%EB%B0%95%EB%AC%BC%EA%B4%80_%EC%83%81%EC%84%A4%EC%A0%84%EC%8B%9C%EC%8B%A4.jpg"),
      Map.entry("서대문 자연사박물관", "https://upload.wikimedia.org/wikipedia/commons/d/df/%EC%84%9C%EB%8C%80%EB%AC%B8%EC%9E%90%EC%97%B0%EC%82%AC%EB%B0%95%EB%AC%BC%EA%B4%80_%EC%A4%91%EC%95%99%ED%99%80.jpg"),
      Map.entry("서울시립미술관", "https://upload.wikimedia.org/wikipedia/commons/6/6d/Seoul_Museum_of_Art_%282014%29.jpg"),
      Map.entry("서울아트센터", "https://upload.wikimedia.org/wikipedia/commons/1/1f/Seoul_Arts_Center_20.JPG"),
      Map.entry("성남아트센터", "https://upload.wikimedia.org/wikipedia/commons/9/9d/Seongnam_Arts_Center.jpg"),
      Map.entry("세종문화회관", "https://upload.wikimedia.org/wikipedia/commons/f/fc/Sejong_Center_for_the_Performing_Arts%2820240413%29.jpg"),
      Map.entry("수원종합운동장", "https://upload.wikimedia.org/wikipedia/commons/4/42/2009-01-24_-_Suwon_Civil_Stadium_from_Royal_Palace.jpg"),
      Map.entry("안산문화예술의전당", "https://upload.wikimedia.org/wikipedia/commons/0/09/230909_%EC%95%88%EC%82%B0%EB%AC%B8%ED%99%94%EC%98%88%EC%88%A0%EC%9D%98%EC%A0%84%EB%8B%B9_%EC%A0%95%EB%A9%B4.jpg"),
      Map.entry("한국만화박물관", "https://upload.wikimedia.org/wikipedia/commons/3/30/Korea_Manhwa_Museum.JPG"),
      Map.entry("한국소리문화의전당", "https://upload.wikimedia.org/wikipedia/commons/a/a2/Moak_Hall_of_the_Sori_Arts_Center_of_Jeollabuk-do.jpg"),
      Map.entry("한밭대학교", "https://upload.wikimedia.org/wikipedia/commons/2/20/%ED%95%9C%EB%B0%AD%EB%8C%80%ED%95%99%EA%B5%90_%EC%9C%A0%EC%84%B1%EC%BA%A0%ED%8D%BC%EC%8A%A4.jpg"),
      Map.entry("함덕해수욕장", "https://upload.wikimedia.org/wikipedia/commons/5/52/Hamdeok_Beach_May_2026_04.jpg")
  );

  public static String resolve(String venueName) {
    return venueName == null ? null : IMAGES.get(venueName);
  }
}
