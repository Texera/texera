package edu.uci.ics.amber.engine.architecture.scheduling.config

import edu.uci.ics.amber.engine.common.PhysicalOpIdentity
import edu.uci.ics.amber.engine.common.PhysicalLink

case class ResourceConfig(
    operatorConfigs: Map[PhysicalOpIdentity, OperatorConfig],
    linkConfigs: Map[PhysicalLink, LinkConfig]
)
