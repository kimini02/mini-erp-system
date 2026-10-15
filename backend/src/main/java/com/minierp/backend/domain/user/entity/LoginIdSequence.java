package com.minierp.backend.domain.user.entity;
import jakarta.persistence.*;
@Entity @Table(name="login_id_sequences")
public class LoginIdSequence {
    @Id private Integer hireYear;
    @Column(nullable=false) private int lastSeq;
}
