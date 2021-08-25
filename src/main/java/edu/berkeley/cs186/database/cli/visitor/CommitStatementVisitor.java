package edu.berkeley.cs186.database.cli.visitor;

/**
 * Purely symbolic class
 */
class CommitStatementVisitor extends StatementVisitor {
    public StatementType getType() {
        return StatementType.COMMIT;
    }
}