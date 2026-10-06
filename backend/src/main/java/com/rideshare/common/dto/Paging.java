package com.rideshare.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Clamps client supplied paging so nobody can request 1 000 000 rows at once.
 */
public final class Paging {

    public static final int MAX_PAGE_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
