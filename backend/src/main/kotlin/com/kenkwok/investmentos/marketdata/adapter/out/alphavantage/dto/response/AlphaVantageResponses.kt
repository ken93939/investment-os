package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

internal data class SymbolSearchResponse(
    @param:JsonProperty("bestMatches") val matches: List<SymbolMatchDto>? = null,
)

internal data class SymbolMatchDto(
    @param:JsonProperty("1. symbol") val symbol: String? = null,
    @param:JsonProperty("2. name") val name: String? = null,
    @param:JsonProperty("3. type") val type: String? = null,
    @param:JsonProperty("4. region") val region: String? = null,
    @param:JsonProperty("7. timezone") val timeZone: String? = null,
    @param:JsonProperty("8. currency") val currency: String? = null,
)

internal data class OverviewResponse(
    @param:JsonProperty("Symbol") val symbol: String? = null,
    @param:JsonProperty("Name") val name: String? = null,
    @param:JsonProperty("AssetType") val assetType: String? = null,
    @param:JsonProperty("Currency") val currency: String? = null,
    @param:JsonProperty("Exchange") val exchange: String? = null,
    @param:JsonProperty("Country") val country: String? = null,
)

internal data class DailyPricesResponse(
    @param:JsonProperty("Meta Data") val metadata: DailyMetadataDto? = null,
    @param:JsonProperty("Time Series (Daily)") val bars: Map<String, DailyBarDto>? = null,
)

internal data class DailyMetadataDto(
    @param:JsonProperty("2. Symbol") val symbol: String? = null,
    @param:JsonProperty("3. Last Refreshed") val lastRefreshed: String? = null,
    @param:JsonProperty("5. Time Zone") val timeZone: String? = null,
)

internal data class DailyBarDto(
    @param:JsonProperty("1. open") val open: String? = null,
    @param:JsonProperty("2. high") val high: String? = null,
    @param:JsonProperty("3. low") val low: String? = null,
    @param:JsonProperty("4. close") val close: String? = null,
    @param:JsonProperty("5. volume") val volume: String? = null,
)

internal data class IncomeStatementResponse(
    val symbol: String? = null,
    val annualReports: List<IncomeReportDto>? = null,
    val quarterlyReports: List<IncomeReportDto>? = null,
)

internal data class IncomeReportDto(
    val fiscalDateEnding: String? = null,
    val reportedCurrency: String? = null,
    val totalRevenue: String? = null,
    val costOfRevenue: String? = null,
    val grossProfit: String? = null,
    val operatingIncome: String? = null,
    val netIncome: String? = null,
)

internal data class BalanceSheetResponse(
    val symbol: String? = null,
    val annualReports: List<BalanceSheetReportDto>? = null,
    val quarterlyReports: List<BalanceSheetReportDto>? = null,
)

internal data class BalanceSheetReportDto(
    val fiscalDateEnding: String? = null,
    val reportedCurrency: String? = null,
    val totalAssets: String? = null,
    val totalLiabilities: String? = null,
    val totalShareholderEquity: String? = null,
    val cashAndCashEquivalentsAtCarryingValue: String? = null,
)

internal data class CashFlowResponse(
    val symbol: String? = null,
    val annualReports: List<CashFlowReportDto>? = null,
    val quarterlyReports: List<CashFlowReportDto>? = null,
)

internal data class CashFlowReportDto(
    val fiscalDateEnding: String? = null,
    val reportedCurrency: String? = null,
    val operatingCashflow: String? = null,
    val capitalExpenditures: String? = null,
    val dividendPayout: String? = null,
)

internal data class MacroSeriesResponse(
    val name: String? = null,
    val interval: String? = null,
    val unit: String? = null,
    val data: List<MacroObservationDto>? = null,
)

internal data class MacroObservationDto(
    val date: String? = null,
    val value: String? = null,
)
