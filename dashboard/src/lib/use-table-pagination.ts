import { useCallback, useEffect, useMemo, useState } from "react";

type UseTablePaginationOptions = {
	initialPageSize?: number;
};

type UseTablePaginationResult<T> = {
	page: number;
	pageSize: number;
	pageCount: number;
	totalItems: number;
	pagedItems: T[];
	setPage: (nextPage: number) => void;
	setPageSize: (nextPageSize: number) => void;
};

function clampPage(page: number, pageCount: number) {
	if (!Number.isFinite(page)) {
		return 1;
	}
	return Math.min(Math.max(1, Math.trunc(page)), pageCount);
}

export function useTablePagination<T>(items: T[], options: UseTablePaginationOptions = {}): UseTablePaginationResult<T> {
	const initialPageSize = options.initialPageSize ?? 10;
	const [page, setPageState] = useState(1);
	const [pageSize, setPageSizeState] = useState(Math.max(1, Math.trunc(initialPageSize)));

	const totalItems = items.length;
	const pageCount = Math.max(1, Math.ceil(totalItems / pageSize));

	useEffect(() => {
		setPageState((current) => clampPage(current, pageCount));
	}, [pageCount]);

	const setPage = useCallback(
		(nextPage: number) => {
			setPageState(clampPage(nextPage, pageCount));
		},
		[pageCount],
	);

	const setPageSize = useCallback((nextPageSize: number) => {
		if (!Number.isFinite(nextPageSize) || nextPageSize < 1) {
			return;
		}
		setPageSizeState(Math.trunc(nextPageSize));
		setPageState(1);
	}, []);

	const pagedItems = useMemo(() => {
		const start = (page - 1) * pageSize;
		return items.slice(start, start + pageSize);
	}, [items, page, pageSize]);

	return {
		page,
		pageSize,
		pageCount,
		totalItems,
		pagedItems,
		setPage,
		setPageSize,
	};
}
