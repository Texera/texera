import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import '../../../common/rxjs-operators';
import { WorkflowActionService } from '../../service/workflow-graph/model/workflow-action.service';
import { OperatorPropertyEditFrameComponent } from './operator-property-edit-frame/operator-property-edit-frame.component';
import { BreakpointPropertyEditFrameComponent } from './breakpoint-property-edit-frame/breakpoint-property-edit-frame.component';
import { Subscription } from 'rxjs';
import { DynamicComponentConfig } from '../../../common/type/dynamic-component-config';


export type PropertyEditFrameComponent = OperatorPropertyEditFrameComponent | BreakpointPropertyEditFrameComponent;

export type PropertyEditFrameConfig = DynamicComponentConfig<PropertyEditFrameComponent>;

/**
 * PropertyEditorComponent is the panel that allows user to edit operator properties.
 * Depending on the highlighted operator or link, it displays OperatorPropertyEditFrameComponent
 * or BreakpointPropertyEditFrameComponent accordingly
 *
 */
@Component({
  selector: 'texera-property-editor',
  templateUrl: './property-editor.component.html',
  styleUrls: ['./property-editor.component.scss']
})
export class PropertyEditorComponent implements OnInit, OnDestroy {

  frameComponentConfig?: PropertyEditFrameConfig;

  subscriptions = new Subscription();


  constructor(public workflowActionService: WorkflowActionService) {}

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  ngOnInit(): void {
    this.registerHighlightEventsHandler();
  }

  switchFrameComponent(targetConfig?: PropertyEditFrameConfig) {
    if (this.frameComponentConfig?.component === targetConfig?.component &&
      this.frameComponentConfig?.componentInputs === targetConfig?.componentInputs) {
      return;
    }

    this.frameComponentConfig = targetConfig;
  }

  /**
   * This method changes the property editor according to how operators are highlighted on the workflow editor.
   *
   * Displays the form of the highlighted operator if only one operator is highlighted;
   * Displays the form of the link breakpoint if only one link is highlighted;
   * hides the form if no operator/link is highlighted or multiple operators and/or groups and/or links are highlighted.
   */
  registerHighlightEventsHandler() {
    this.subscriptions.add(Observable.merge(
      this.workflowActionService.getJointGraphWrapper().getJointOperatorHighlightStream(),
      this.workflowActionService.getJointGraphWrapper().getJointOperatorUnhighlightStream(),
      this.workflowActionService.getJointGraphWrapper().getJointGroupHighlightStream(),
      this.workflowActionService.getJointGraphWrapper().getJointGroupUnhighlightStream(),
      this.workflowActionService.getJointGraphWrapper().getLinkHighlightStream(),
      this.workflowActionService.getJointGraphWrapper().getLinkUnhighlightStream()
    ).subscribe(() => {
      const highlightedOperators = this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedOperatorIDs();
      const highlightedGroups = this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedGroupIDs();
      const highlightLinks = this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedLinkIDs();

      if (highlightedOperators.length === 1 && highlightedGroups.length === 0 && highlightLinks.length === 0) {
        this.switchFrameComponent({
          component: OperatorPropertyEditFrameComponent,
          componentInputs: { currentOperatorId: highlightedOperators[0] }
        });
      } else if (highlightLinks.length === 1 && highlightedGroups.length === 0 && highlightedOperators.length === 0) {
        this.switchFrameComponent({
          component: BreakpointPropertyEditFrameComponent,
          componentInputs: { currentLinkId: highlightLinks[0] }
        });
      } else {
        this.switchFrameComponent(undefined);
      }

    }));
  }


}
