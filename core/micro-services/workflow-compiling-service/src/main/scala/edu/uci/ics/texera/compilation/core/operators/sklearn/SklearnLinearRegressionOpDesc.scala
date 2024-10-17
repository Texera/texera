package edu.uci.ics.texera.compilation.core.operators.sklearn

class SklearnLinearRegressionOpDesc extends SklearnMLOpDesc {
  override def getImportStatements = "from sklearn.linear_model import LinearRegression"
  override def getUserFriendlyModelName = "Linear Regression"
  classification = false
}
