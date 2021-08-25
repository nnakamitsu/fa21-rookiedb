package edu.berkeley.cs186.database.cli;

import edu.berkeley.cs186.database.databox.*;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.table.Table;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class PrettyPrinter {
    PrintStream out;

    public PrettyPrinter() {
        this(System.out);
    }

    public PrettyPrinter(PrintStream out) {
        this.out = out;
    }

    public void printTable(Table t) {
        Schema s = t.getSchema();
        printRecords(s.getFieldNames(), t.iterator());
    }

    public void printSchema(Schema s) {
        List<Record> records = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            records.add(new Record(Arrays.asList(
                new StringDataBox(s.getFieldName(i), 32),
                new StringDataBox(s.getFieldType(i).toString(), 32)
            )));
        }
        printRecords(Arrays.asList("column_name", "type"), records.iterator());
    }

    public void printRecords(List<String> columnNames, Iterator<Record> records) {
        ArrayList<Integer> maxWidths = new ArrayList<>();
        for(String columnName: columnNames) {
            maxWidths.add(columnName.length());
        }
        ArrayList<Record> recordList = new ArrayList<Record>();
        while(records.hasNext()) {
            Record record = records.next();
            recordList.add(record);
            List<DataBox> fields = record.getValues();
            for(int i = 0; i < fields.size(); i++) {
                DataBox field = fields.get(i);
                maxWidths.set(i, Integer.max(
                    maxWidths.get(i),
                    field.toString().replace("\0", "").length()
                ));
            }
        }
        printRow(columnNames, maxWidths);
        printSeparator(maxWidths);
        for(Record record: recordList) {
            printRecord(record, maxWidths);
        }
        if (recordList.size() != 1) {
            this.out.printf("(%d rows)\n", recordList.size());
        } else {
            this.out.printf("(%d row)\n", recordList.size());
        }
    }

    private void printRow(List<String> values, List<Integer> padding) {
        for(int i = 0; i < values.size(); i++) {
            if (i > 0) this.out.print("|");
            String curr = values.get(i);
            if (i == values.size() - 1) {
                this.out.println(" " + curr);
                break;
            }
            this.out.printf(" %-"+padding.get(i)+"s ", curr);
        }
    }

    public void printRecord(Record record, List<Integer> padding) {
        List<String> row = new ArrayList<>();
        List<DataBox> values = record.getValues();
        for (int i = 0; i < values.size(); i++) {
            DataBox field = values.get(i);
            String cleaned = field.toString().replace("\0", "");
            if(field.type().equals(Type.longType()) || field.type().equals(Type.intType())) {
                cleaned = String.format("%" + padding.get(i) + "s", cleaned);
            }
            row.add(cleaned);
        }
        printRow(row, padding);
    }

    private void printSeparator(List<Integer> padding) {
        for(int i = 0; i < padding.size(); i++) {
            if (i > 0) this.out.print("+");
            for(int j = 0; j < padding.get(i) + 2; j++)
                this.out.print("-");
        }
        this.out.println();
    }

    public static DataBox parseLiteral(String literal) {
        String literalLower = literal.toLowerCase();
        if(literal.charAt(0) == '\'') {
            String unescaped = literal.substring(1, literal.length() - 1);
            String escaped = unescaped.replace("''", "''");
            return new StringDataBox(escaped, escaped.length());
        } else if(literalLower.equals("true")) {
            return new BoolDataBox(true);
        } else if(literalLower.equals("false")){
            return new BoolDataBox(false);
        } if (literal.indexOf('.') != -1) {
            return new FloatDataBox(Float.parseFloat(literal));
        } else {
            return new IntDataBox(Integer.parseInt(literal));
        }
    }
}