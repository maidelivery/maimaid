import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { TablePagination } from "@/components/ui/table-pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { AdminUserRow } from "@/lib/app-types";
import { useTablePagination } from "@/lib/use-table-pagination";
import { RefreshCwIcon, Trash2Icon } from "lucide-react";
import { useTranslation } from "react-i18next";

type AdminUsersPageProps = {
	newUserEmail: string;
	newUserPassword: string;
	adminUsers: AdminUserRow[];
	onNewUserEmailChange: (value: string) => void;
	onNewUserPasswordChange: (value: string) => void;
	onCreateUser: () => void | Promise<void>;
	onReloadUsers: () => void | Promise<void>;
	onDeleteUser: (userId: string) => void | Promise<void>;
};

export function AdminUsersPage({
	newUserEmail,
	newUserPassword,
	adminUsers,
	onNewUserEmailChange,
	onNewUserPasswordChange,
	onCreateUser,
	onReloadUsers,
	onDeleteUser,
}: AdminUsersPageProps) {
	const { t } = useTranslation("adminUsers");
	const pagination = useTablePagination(adminUsers);

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t("pageTitle")}</CardTitle>
				<CardDescription>{t("pageDesc")}</CardDescription>
			</CardHeader>
			<CardContent className="flex flex-col gap-6">
				<section className="rounded-lg border p-4">
					<div className="mb-3 text-sm font-medium">{t("sectionCreateUser")}</div>
					<p className="mb-3 text-sm text-muted-foreground">{t("createHandleHint")}</p>
					<FieldGroup className="gap-3 md:flex-row md:items-end">
						<Field className="min-w-0 flex-1">
							<FieldLabel htmlFor="create-user-email">{t("labelEmail")}</FieldLabel>
							<Input
								id="create-user-email"
								type="email"
								value={newUserEmail}
								onChange={(event) => onNewUserEmailChange(event.target.value)}
							/>
						</Field>
						<Field className="min-w-0 flex-1">
							<FieldLabel htmlFor="create-user-password">{t("labelPassword")}</FieldLabel>
							<Input
								id="create-user-password"
								type="password"
								value={newUserPassword}
								onChange={(event) => onNewUserPasswordChange(event.target.value)}
							/>
						</Field>
						<Button className="w-full md:w-auto md:shrink-0" onClick={() => void onCreateUser()}>
							{t("btnCreate")}
						</Button>
					</FieldGroup>
				</section>

				<div className="flex flex-wrap gap-2">
					<Button variant="outline" onClick={() => void onReloadUsers()}>
						<RefreshCwIcon data-icon="inline-start" />
						{t("btnRefresh")}
					</Button>
				</div>

				{adminUsers.length === 0 ? (
					<Empty>
						<EmptyHeader>
							<EmptyTitle>{t("noDataTitle")}</EmptyTitle>
							<EmptyDescription>{t("noDataDesc")}</EmptyDescription>
						</EmptyHeader>
						<EmptyContent>
							<Button variant="outline" onClick={() => void onReloadUsers()}>
								<RefreshCwIcon data-icon="inline-start" />
								{t("btnRefreshNow")}
							</Button>
						</EmptyContent>
					</Empty>
				) : (
					<div className="flex flex-col gap-3">
						<div className="space-y-3 md:hidden">
							{pagination.pagedItems.map((row) => (
								<article key={row.id} className="rounded-lg border p-3">
									<p className="break-all text-sm font-medium">{row.handle}</p>
									<p className="mt-1 break-all text-xs text-muted-foreground">{row.email}</p>
									<div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
										<span className="rounded-md border px-2 py-1">
											{t("statusPrefix")}
											{row.status}
										</span>
										<span className="rounded-md border px-2 py-1">
											{t("adminPrefix")}
											{row.isAdmin ? t("yes") : t("no")}
										</span>
									</div>
									<p className="mt-2 text-xs text-muted-foreground">
										{t("mfaPrefix")}
										{row.mfa.enabled
											? `TOTP:${row.mfa.totpEnabled ? t("on") : t("off")}, Passkey:${row.mfa.passkeyCount}`
											: t("off")}
									</p>
									<p className="mt-1 text-xs text-muted-foreground">
										{t("createdAtPrefix")}
										{new Date(row.createdAt).toLocaleString()}
									</p>
									<Button variant="destructive" className="mt-3 h-9 w-full" onClick={() => void onDeleteUser(row.id)}>
										<Trash2Icon data-icon="inline-start" />
										{t("btnDelete")}
									</Button>
								</article>
							))}
						</div>

						<div className="hidden md:block">
							<Table>
								<TableHeader>
									<TableRow>
										<TableHead>{t("colHandle")}</TableHead>
										<TableHead>{t("colEmail")}</TableHead>
										<TableHead>{t("colStatus")}</TableHead>
										<TableHead>{t("colAdmin")}</TableHead>
										<TableHead>{t("colMfa")}</TableHead>
										<TableHead>{t("colCreated")}</TableHead>
										<TableHead>{t("colActions")}</TableHead>
									</TableRow>
								</TableHeader>
								<TableBody>
									{pagination.pagedItems.map((row) => (
										<TableRow key={row.id}>
											<TableCell className="max-w-[220px] truncate font-medium">{row.handle}</TableCell>
											<TableCell className="max-w-[280px] truncate">{row.email}</TableCell>
											<TableCell>{row.status}</TableCell>
											<TableCell>{row.isAdmin ? t("yes") : t("no")}</TableCell>
											<TableCell>
												{row.mfa.enabled
													? `TOTP:${row.mfa.totpEnabled ? t("on") : t("off")}, Passkey:${row.mfa.passkeyCount}`
													: t("off")}
											</TableCell>
											<TableCell>{new Date(row.createdAt).toLocaleString()}</TableCell>
											<TableCell>
												<Button variant="outline" size="sm" onClick={() => void onDeleteUser(row.id)}>
													<Trash2Icon data-icon="inline-start" />
													{t("btnDelete")}
												</Button>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</div>

						<TablePagination
							page={pagination.page}
							pageCount={pagination.pageCount}
							pageSize={pagination.pageSize}
							onPageChange={pagination.setPage}
							onPageSizeChange={pagination.setPageSize}
						/>
					</div>
				)}
			</CardContent>
		</Card>
	);
}
