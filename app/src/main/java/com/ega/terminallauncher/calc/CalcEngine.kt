package com.ega.terminallauncher.calc

import java.util.ArrayDeque
import kotlin.math.pow

sealed interface CalcResult {
    data class Success(val value: Double, val formatted: String) : CalcResult
    data class Error(val message: String) : CalcResult
}

object CalcEngine {

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Operator(val symbol: Char, val precedence: Int, val isRightAssociative: Boolean) : Token
        object UnaryMinus : Token
        object LeftParen : Token
        object RightParen : Token
    }

    fun evaluate(rawExpr: String): CalcResult {
        val expr = rawExpr.trim()
        if (expr.isEmpty()) {
            return CalcResult.Error("[Usage: calc <expression>]")
        }

        val tokens = tokenize(expr) ?: return CalcResult.Error("[Error: Invalid expression]")
        if (tokens.isEmpty()) {
            return CalcResult.Error("[Usage: calc <expression>]")
        }

        val rpn = toRpn(tokens)
        if (rpn is RpnResult.Error) {
            return CalcResult.Error(rpn.message)
        }
        val rpnTokens = (rpn as RpnResult.Success).tokens

        return evaluateRpn(rpnTokens)
    }

    private fun tokenize(expr: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        var expectOperand = true

        while (i < expr.length) {
            val c = expr[i]

            if (c.isWhitespace()) {
                i++
                continue
            }

            if (c.isDigit() || c == '.') {
                val start = i
                var hasDot = (c == '.')
                i++
                while (i < expr.length) {
                    val nextChar = expr[i]
                    if (nextChar.isDigit()) {
                        i++
                    } else if (nextChar == '.') {
                        if (hasDot) return null // Multiple decimal points
                        hasDot = true
                        i++
                    } else {
                        break
                    }
                }
                val numStr = expr.substring(start, i)
                val num = numStr.toDoubleOrNull() ?: return null
                tokens.add(Token.Number(num))
                expectOperand = false
            } else if (c == '(') {
                tokens.add(Token.LeftParen)
                expectOperand = true
                i++
            } else if (c == ')') {
                if (expectOperand) return null // Empty parentheses or operator before ')'
                tokens.add(Token.RightParen)
                expectOperand = false
                i++
            } else if (c == '-' && expectOperand) {
                tokens.add(Token.UnaryMinus)
                expectOperand = true
                i++
            } else if (isBinaryOperator(c)) {
                if (expectOperand) return null // Consecutive binary operators without operand
                val precedence = when (c) {
                    '+', '-' -> 1
                    '*', '/', '%' -> 2
                    '^' -> 3
                    else -> 0
                }
                val isRightAssoc = (c == '^')
                tokens.add(Token.Operator(c, precedence, isRightAssoc))
                expectOperand = true
                i++
            } else {
                return null // Unrecognized character
            }
        }

        if (expectOperand && tokens.isNotEmpty()) {
            return null // Trailing operator
        }

        return tokens
    }

    private fun isBinaryOperator(c: Char): Boolean {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^'
    }

    private sealed interface RpnResult {
        data class Success(val tokens: List<Token>) : RpnResult
        data class Error(val message: String) : RpnResult
    }

    private fun toRpn(tokens: List<Token>): RpnResult {
        val output = mutableListOf<Token>()
        val opStack = ArrayDeque<Token>()

        for (token in tokens) {
            when (token) {
                is Token.Number -> output.add(token)
                is Token.UnaryMinus -> {
                    // Unary minus has precedence 4, right-associative
                    opStack.push(token)
                }
                is Token.Operator -> {
                    while (opStack.isNotEmpty()) {
                        val top = opStack.peek()
                        if (top is Token.LeftParen) break

                        val topPrecedence = when (top) {
                            is Token.Operator -> top.precedence
                            is Token.UnaryMinus -> 4
                            else -> 0
                        }

                        val shouldPop = if (token.isRightAssociative) {
                            token.precedence < topPrecedence
                        } else {
                            token.precedence <= topPrecedence
                        }

                        if (shouldPop) {
                            output.add(opStack.pop())
                        } else {
                            break
                        }
                    }
                    opStack.push(token)
                }
                is Token.LeftParen -> {
                    opStack.push(token)
                }
                is Token.RightParen -> {
                    var foundLeft = false
                    while (opStack.isNotEmpty()) {
                        val top = opStack.pop()
                        if (top is Token.LeftParen) {
                            foundLeft = true
                            break
                        }
                        output.add(top)
                    }
                    if (!foundLeft) {
                        return RpnResult.Error("[Error: Mismatched parentheses]")
                    }
                }
            }
        }

        while (opStack.isNotEmpty()) {
            val top = opStack.pop()
            if (top is Token.LeftParen || top is Token.RightParen) {
                return RpnResult.Error("[Error: Mismatched parentheses]")
            }
            output.add(top)
        }

        return RpnResult.Success(output)
    }

    private fun evaluateRpn(tokens: List<Token>): CalcResult {
        val stack = ArrayDeque<Double>()

        for (token in tokens) {
            when (token) {
                is Token.Number -> stack.push(token.value)
                is Token.UnaryMinus -> {
                    if (stack.isEmpty()) return CalcResult.Error("[Error: Invalid expression]")
                    val a = stack.pop()
                    stack.push(-a)
                }
                is Token.Operator -> {
                    if (stack.size < 2) return CalcResult.Error("[Error: Invalid expression]")
                    val b = stack.pop()
                    val a = stack.pop()

                    val result = when (token.symbol) {
                        '+' -> a + b
                        '-' -> a - b
                        '*' -> a * b
                        '/' -> {
                            if (b == 0.0) return CalcResult.Error("[Error: Division by zero]")
                            a / b
                        }
                        '%' -> {
                            if (b == 0.0) return CalcResult.Error("[Error: Division by zero]")
                            a % b
                        }
                        '^' -> a.pow(b)
                        else -> return CalcResult.Error("[Error: Invalid expression]")
                    }

                    if (result.isNaN() || (result.isInfinite() && (token.symbol == '/' || token.symbol == '%'))) {
                        return CalcResult.Error("[Error: Division by zero]")
                    }
                    if (result.isInfinite()) {
                        return CalcResult.Error("[Error: Invalid expression]")
                    }

                    stack.push(result)
                }
                else -> return CalcResult.Error("[Error: Invalid expression]")
            }
        }

        if (stack.size != 1) {
            return CalcResult.Error("[Error: Invalid expression]")
        }

        val finalValue = stack.pop()
        return CalcResult.Success(finalValue, formatNumber(finalValue))
    }

    private fun formatNumber(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = kotlin.math.round(value)
        if (kotlin.math.abs(value - rounded) < 1e-9 && kotlin.math.abs(value) < 1e15) {
            return rounded.toLong().toString()
        }
        val df = java.text.DecimalFormat("#.##########", java.text.DecimalFormatSymbols(java.util.Locale.US))
        df.maximumFractionDigits = 10
        return df.format(value)
    }
}
