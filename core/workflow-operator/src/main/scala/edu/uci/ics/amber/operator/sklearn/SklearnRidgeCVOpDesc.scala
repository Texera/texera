package edu.uci.ics.amber.operator.sklearn

class SklearnRidgeCVOpDesc extends SklearnClassifierOpDesc {
  override def getImportStatements = "from sklearn.linear_model import RidgeClassifierCV"
  override def getUserFriendlyModelName = "Ridge Regression Cross Validation"
}
