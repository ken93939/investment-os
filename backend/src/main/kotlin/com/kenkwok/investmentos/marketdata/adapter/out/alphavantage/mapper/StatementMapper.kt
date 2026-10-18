package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.BalanceSheetResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.CashFlowResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.IncomeStatementResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.malformed
import com.kenkwok.investmentos.marketdata.domain.FinancialField
import com.kenkwok.investmentos.marketdata.domain.FinancialReport
import com.kenkwok.investmentos.marketdata.domain.FinancialStatements
import com.kenkwok.investmentos.marketdata.domain.ReportPeriod
import com.kenkwok.investmentos.marketdata.domain.StatementType
import com.kenkwok.investmentos.marketdata.domain.Symbol
import java.time.LocalDate
import java.util.Currency

internal class StatementMapper {
    fun income(
        response: IncomeStatementResponse,
        symbol: Symbol,
        period: ReportPeriod,
    ): FinancialStatements =
        mapping {
            verifySymbol(response.symbol, symbol)
            val reports = (if (period == ReportPeriod.ANNUAL) response.annualReports else response.quarterlyReports).requiredValue()
            finish(
                symbol,
                StatementType.INCOME,
                reports.map { row ->
                    FinancialReport(
                        LocalDate.parse(row.fiscalDateEnding.required()),
                        period,
                        Currency.getInstance(row.reportedCurrency.required()),
                        mapOf(
                            FinancialField.REVENUE to row.totalRevenue.decimal(),
                            FinancialField.COST_OF_REVENUE to row.costOfRevenue.decimal(),
                            FinancialField.GROSS_PROFIT to row.grossProfit.decimal(),
                            FinancialField.OPERATING_INCOME to row.operatingIncome.decimal(),
                            FinancialField.NET_INCOME to row.netIncome.decimal(),
                        ),
                    )
                },
            )
        }

    fun balanceSheet(
        response: BalanceSheetResponse,
        symbol: Symbol,
        period: ReportPeriod,
    ): FinancialStatements =
        mapping {
            verifySymbol(response.symbol, symbol)
            val reports = (if (period == ReportPeriod.ANNUAL) response.annualReports else response.quarterlyReports).requiredValue()
            finish(
                symbol,
                StatementType.BALANCE_SHEET,
                reports.map { row ->
                    FinancialReport(
                        LocalDate.parse(row.fiscalDateEnding.required()),
                        period,
                        Currency.getInstance(row.reportedCurrency.required()),
                        mapOf(
                            FinancialField.TOTAL_ASSETS to row.totalAssets.decimal(),
                            FinancialField.TOTAL_LIABILITIES to row.totalLiabilities.decimal(),
                            FinancialField.SHAREHOLDER_EQUITY to row.totalShareholderEquity.decimal(),
                            FinancialField.CASH_AND_EQUIVALENTS to row.cashAndCashEquivalentsAtCarryingValue.decimal(),
                        ),
                    )
                },
            )
        }

    fun cashFlow(
        response: CashFlowResponse,
        symbol: Symbol,
        period: ReportPeriod,
    ): FinancialStatements =
        mapping {
            verifySymbol(response.symbol, symbol)
            val reports = (if (period == ReportPeriod.ANNUAL) response.annualReports else response.quarterlyReports).requiredValue()
            finish(
                symbol,
                StatementType.CASH_FLOW,
                reports.map { row ->
                    FinancialReport(
                        LocalDate.parse(row.fiscalDateEnding.required()),
                        period,
                        Currency.getInstance(row.reportedCurrency.required()),
                        mapOf(
                            FinancialField.OPERATING_CASH_FLOW to row.operatingCashflow.decimal(),
                            FinancialField.CAPITAL_EXPENDITURE to row.capitalExpenditures.decimal(),
                            FinancialField.DIVIDENDS_PAID to row.dividendPayout.decimal(),
                        ),
                    )
                },
            )
        }

    private fun verifySymbol(
        actual: String?,
        expected: Symbol,
    ) {
        if (Symbol.parse(actual.required()) != expected) malformed()
    }

    private fun finish(
        symbol: Symbol,
        type: StatementType,
        reports: List<FinancialReport>,
    ): FinancialStatements {
        if (reports.map { it.fiscalPeriodEnd }.distinct().size != reports.size) malformed()
        return FinancialStatements(symbol, type, reports.sortedBy { it.fiscalPeriodEnd })
    }
}
