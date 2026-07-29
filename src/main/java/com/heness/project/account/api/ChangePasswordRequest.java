package com.heness.project.account.api;

record ChangePasswordRequest(
		String currentPassword,
		String newPassword,
		String confirmNewPassword) {
}
