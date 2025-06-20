package com.fiec.voz_cidada.repository;

import com.fiec.voz_cidada.domain.funcionario.Funcionario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FuncionarioRepository extends GenericRepository<Funcionario, Long> {
    Optional<Funcionario> findByAuthUser_Id(Long id);
}
