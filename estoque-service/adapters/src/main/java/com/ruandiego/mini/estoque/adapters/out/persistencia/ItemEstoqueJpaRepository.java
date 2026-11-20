package com.ruandiego.mini.estoque.adapters.out.persistencia;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

interface ItemEstoqueJpaRepository extends JpaRepository<ItemEstoqueJpa, String> {

    /**
     * `SELECT ... FOR UPDATE` ordenado por id: as linhas são travadas sempre na mesma ordem, então duas
     * reservas com produtos em comum esperam uma pela outra em vez de travar em ciclo (deadlock).
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ItemEstoqueJpa i where i.produtoId in :ids order by i.produtoId")
    List<ItemEstoqueJpa> buscarComTrava(@Param("ids") Collection<String> ids);
}
