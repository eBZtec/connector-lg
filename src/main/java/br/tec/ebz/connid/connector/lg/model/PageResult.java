package br.tec.ebz.connid.connector.lg.model;

import java.util.List;

public record PageResult(List<UserRecord> users, int totalPages) {}