package edu.uci.ics.texera.compilation.core.operators.sklearn

class SklearnBaggingOpDesc extends SklearnMLOpDesc {
  override def getImportStatements = "from sklearn.ensemble import BaggingClassifier"
  override def getUserFriendlyModelName = "Bagging"
}
