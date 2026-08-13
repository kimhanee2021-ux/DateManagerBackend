package org.ict.datemanagerbackend.domain.calendar.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.ict.datemanagerbackend.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
//테이블 명
@Table(name="user_calendars")

public class UserCalendar {
  //PK 설정
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

//FK 설정
@ManyToOne
@JoinColumn(name = "user_id")
  private User user;

@Column(nullable = false,length = 100)
  private String title;

@Column
//엄청 큰 4G 짜리라는 뜻.
@Lob
  private String description;

@Column(nullable = false)
private LocalDate targetDate;

//미완성
@Column
  private Long courseGroupId;

@Column(nullable = false,insertable = false,updatable = false)
@ColumnDefault("SYSTIMESTAMP")
  private LocalDateTime createdAt;








}
