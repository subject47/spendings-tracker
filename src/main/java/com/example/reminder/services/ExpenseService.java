package com.example.reminder.services;

import com.example.reminder.domain.Category;
import com.example.reminder.domain.Expense;
import com.example.reminder.repositories.ExpenseRepository;
import com.example.reminder.utils.DateUtils;
import com.google.common.collect.Lists;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExpenseService implements CRUDService<Expense> {

  private final ExpenseRepository expenseRepository;
  private final CategoryService categoryService;

  @Autowired
  public ExpenseService(ExpenseRepository expenseRepository, CategoryService categoryService) {
    this.expenseRepository = expenseRepository;
    this.categoryService = categoryService;
  }

  public List<Expense> listAll() {
    List<Expense> expenses = new ArrayList<>();
    expenseRepository.findAll().forEach(expenses::add);
    return expenses;
  }

  public Expense getById(Integer id) {
    return expenseRepository.findById(id).orElse(null);
  }

  public Expense save(Expense expense) {
    return expenseRepository.save(expense);
  }

  public void delete(Integer id) {
    expenseRepository.deleteById(id);
  }

  public List<Expense> findAllExpensesByYearAndMonth(Year year, Month month) {
    LocalDate start = LocalDate.of(year.getValue(), month, 1);
    LocalDate end = start.plusMonths(1).minusDays(1);
    return expenseRepository.findByDateBetween(DateUtils.asDate(start), DateUtils.asDate(end));
  }

  public List<Expense> findExpensesByYearAndMonthAndUsername(Year year, Month month, String username) {
    return findAllExpensesByYearAndMonth(year, month)
        .stream()
        .filter(e -> e.getUser().getUsername().equals(username))
        .sorted(Comparator.comparing(Expense::getDate))
        .collect(Collectors.toList());
  }

  public List<List<Object>> buildDataGrid(Year year, Month month, String username) {
    List<Expense> expenses =
        findExpensesByYearAndMonthAndUsername(year, month, username).stream()
            .sorted()
            .collect(Collectors.toList());
    List<Category> categories = categoryService.listAll();
    int numberOfRows = month.length(year.isLeap()) + 2;
    int rowSize = categories.size() + 3;
    Map<Date, Double> totalAmountByDate = findTotalAmountsPerDates(expenses);
    List<List<Object>> grid = initializeDataGrid(numberOfRows, rowSize);
    grid.set(0, buildDataGridHeader(categories));
    expenses.stream()
        .sorted()
        .forEach(e -> addRowToDataGrid(e, grid, totalAmountByDate));
    grid.set(numberOfRows - 1, buildDataGridFooter(grid));
    return grid;
  }

  private List<Object> buildDataGridHeader(List<Category> categories) {
    List<Object> header = Lists.newArrayListWithCapacity(categories.size() + 3);
    header.add("");  // expenseId hidden column
    header.add("");  // day of month column
    header.addAll(categories.stream()
        .sorted()
        .map(Category::getName)
        .collect(Collectors.toList()));
    header.add("Total");
    return header;
  }

  private void addRowToDataGrid(Expense expense, List<List<Object>> grid, Map<Date, Double> totalAmountByDate) {
    int dayOfMonth = DateUtils.asLocalDate(expense.getDate()).getDayOfMonth();
    double totalAmount = totalAmountByDate.get(expense.getDate());
    List<Object> row = grid.get(dayOfMonth);
    row.set(0, expense.getId());
    row.set(expense.getCategory().getPriority() + 1, expense.getAmount());
    row.set(row.size() - 1, totalAmount);
  }

  private Map<Date, Double> findTotalAmountsPerDates(List<Expense> expenses) {
    return expenses.stream()
        .collect(Collectors.groupingBy(Expense::getDate))
        .entrySet().stream()
        .map(e ->
            Pair.of(e.getKey(),
                e.getValue().stream()
                    .map(Expense::getAmount)
                    .reduce(0.0, (a, b) -> a + b)))
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  private List<Object> buildDataGridFooter(List<List<Object>> grid) {
    int rowSize = grid.get(0).size();
    int columnsToCount = rowSize - 2;
    List<Object> footer = Lists.newArrayListWithCapacity(rowSize);
    // Assign initial values
    for (int i = 0; i < rowSize; i++) {
      footer.add(i, 0.0);
    }
    footer.set(0, "");
    footer.set(1, "Total");
    // Aggregate the values in footer
    grid.stream().forEach(r -> {
      for (int i = rowSize - columnsToCount; i < rowSize; i++) {
        if (r.get(i) instanceof String) {
          continue;
        }
        double value = (Double) r.get(i);
        double aggregatedValue = (Double) footer.get(i);
        aggregatedValue += value;
        footer.set(i, aggregatedValue);
      }
    });
    return footer;
  }

  private List<List<Object>> initializeDataGrid(int numberOfRows, int rowSize) {
    List<List<Object>> grid = Lists.newArrayListWithCapacity(numberOfRows);
    for (int i = 0; i < numberOfRows; i++) {
      List<Object> row = Lists.newArrayListWithCapacity(rowSize);
      for (int j = 0; j < rowSize; j ++) {
        row.add("");
      }
      row.set(1, i);
      grid.add(row);
    }
    return grid;
  }
}
