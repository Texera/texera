import { UndoRedoService } from './../../undo-redo/undo-redo.service';
import { OperatorMetadataService } from './../../operator-metadata/operator-metadata.service';
import { SyncTexeraModel } from './sync-texera-model';
import { JointGraphWrapper } from './joint-graph-wrapper';
import { JointUIService } from './../../joint-ui/joint-ui.service';
import { WorkflowGraph, WorkflowGraphReadonly } from './workflow-graph';
import { Injectable } from '@angular/core';
import { Point, OperatorPredicate, OperatorLink, OperatorPort } from '../../../types/workflow-common.interface';

import * as joint from 'jointjs';
import { Observable, Operator } from 'rxjs';


export interface Command {
  execute(): void;
  undo(): void;
  redo?(): void;
}

/**
 *
 * WorkflowActionService exposes functions (actions) to modify the workflow graph model of both JointJS and Texera,
 *  such as addOperator, deleteOperator, addLink, deleteLink, etc.
 * WorkflowActionService performs checks the validity of these actions,
 *  for example, throws an error if deleting an nonexist operator
 *
 * All changes(actions) to the workflow graph should be called through WorkflowActionService,
 *  then WorkflowActionService will propagate these actions to JointModel and Texera Model automatically.
 *
 * For an overview of the services in WorkflowGraphModule, see workflow-graph-design.md
 *
 */


@Injectable()
export class WorkflowActionService {

  private readonly texeraGraph: WorkflowGraph;
  private readonly jointGraph: joint.dia.Graph;
  private readonly jointGraphWrapper: JointGraphWrapper;
  private readonly syncTexeraModel: SyncTexeraModel;

  constructor(
    private operatorMetadataService: OperatorMetadataService,
    private jointUIService: JointUIService,
    private undoRedoService: UndoRedoService
  ) {
    this.texeraGraph = new WorkflowGraph();
    this.jointGraph = new joint.dia.Graph();
    this.jointGraphWrapper = new JointGraphWrapper(this.jointGraph);
    this.syncTexeraModel = new SyncTexeraModel(this.texeraGraph, this.jointGraphWrapper);

    this.handleJointLinkAdd();
    this.handleJointOperatorDrag();
  }

  public handleJointLinkAdd(): void {
    this.texeraGraph.getLinkAddStream().filter(() => this.undoRedoService.listenJointCommand).subscribe(link => {
      const command: Command = {
        execute: () => { },
        undo: () => this.deleteLinkWithIDInternal(link.linkID),
        redo: () => this.addLinkInternal(link)
      };
      this.executeAndStoreCommand(command);
    });
  }

  public handleJointOperatorDrag(): void {
    let oldPosition: Point = {x: 0, y: 0};
    let gotOldPosition = false;
    this.jointGraphWrapper.getOperatorPositionChangeEvent()
      .filter(() => !gotOldPosition)
      .filter(() => this.undoRedoService.listenJointCommand)
      .subscribe(event => {
        oldPosition = event.oldPosition;
        gotOldPosition = true;
      });

    this.jointGraphWrapper.getOperatorPositionChangeEvent()
      .filter(() => this.undoRedoService.listenJointCommand)
      .debounceTime(100)
      .subscribe(event => {
        gotOldPosition = false;
        const currentOldPos = oldPosition;
        const currentPos = event.newPosition;
        const command: Command = {
          execute: () => { },
          undo: () => this.setOperatorPositionInternal(event.operatorID, currentOldPos),
          redo: () => this.setOperatorPositionInternal(event.operatorID, currentPos) // work here
        };
        this.executeAndStoreCommand(command);
      });
  }

  /**
   * Gets the read-only version of the TexeraGraph
   *  to access the properties and event streams.
   *
   * Texera Graph contains information about the logical workflow plan of Texera,
   *  such as the types and properties of the operators.
   */
  public getTexeraGraph(): WorkflowGraphReadonly {
    return this.texeraGraph;
  }

  /**
   * Gets the JointGraph Wrapper, which contains
   *  getter for properties and event streams as RxJS Observables.
   *
   * JointJS Graph contains information about the UI,
   *  such as the position of operator elements, and the event of user dragging a cell around.
   */
  public getJointGraphWrapper(): JointGraphWrapper {
    return this.jointGraphWrapper;
  }

  /**
   * Let the JointGraph model be attached to the joint paper (paperOptions will be passed to Joint Paper constructor).
   *
   * We don't want to expose JointModel as a public variable, so instead we let JointPaper to pass the constructor options,
   *  and JointModel can be still attached to it without being publicly accessible by other modules.
   *
   * @param paperOptions JointJS paper options
   */
  public attachJointPaper(paperOptions: joint.dia.Paper.Options): joint.dia.Paper.Options {
    paperOptions.model = this.jointGraph;
    return paperOptions;
  }

  /**
   * Adds an opreator to the workflow graph at a point.
   * Throws an Error if the operator ID already existed in the Workflow Graph.
   *
   * @param operator
   * @param point
   */
  public addOperator(operator: OperatorPredicate, point: Point): void {
    // check that the operator type exists
    if (!this.operatorMetadataService.operatorTypeExists(operator.operatorType)) {
      throw new Error(`operator type ${operator.operatorType} is invalid`);
    }
    // remember currently highlighted operator
    const currentHighlighted = this.jointGraphWrapper.getCurrentHighlightedOpeartorID();

    const command: Command = {
      execute: () => {
        this.addOperatorInternal(operator, point);
        // highlight the newly added operator
        this.jointGraphWrapper.highlightOperator(operator.operatorID);
      },
      undo: () => {
        // remove the operator from JointJS
        this.deleteOperatorInternal(operator.operatorID);
        // JointJS operator delete event will propagate and trigger Texera operator delete
        this.jointGraphWrapper.highlightOperator(currentHighlighted);
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
    * Deletes an operator from the workflow graph
    * Throws an Error if the operator ID doesn't exist in the Workflow Graph.
    * @param operatorID
    */
  public deleteOperator(operatorID: string): void {
    const operator = this.getTexeraGraph().getOperator(operatorID);
    const position = this.getJointGraphWrapper().getOperatorPosition(operatorID);
    const linksToDelete = this.getTexeraGraph().getAllLinks()
      .filter(link => link.source.operatorID === operatorID || link.target.operatorID === operatorID);

    const command: Command = {
      execute: () => {
        linksToDelete.forEach(link => this.deleteLinkWithIDInternal(link.linkID));
        this.deleteOperatorInternal(operatorID);
      },
      undo: () => {
        this.addOperatorInternal(operator, position);
        linksToDelete.forEach(link => this.addLinkInternal(link));
        this.getJointGraphWrapper().highlightOperator(operator.operatorID);
      }
    };
    this.executeAndStoreCommand(command);
  }

  public addOperatorsAndLinks(operatorsAndPositions: {op: OperatorPredicate, pos: Point}[], links: OperatorLink[]): void {
    // // check that the operator type exists
    // if (!this.operatorMetadataService.operatorTypeExists(operator.operatorType)) {
    //   throw new Error(`operator type ${operator.operatorType} is invalid`);
    // }
    // remember currently highlighted operator
    // const currentHighlighted = this.jointGraphWrapper.getCurrentHighlightedOpeartorID();

    const command: Command = {
      execute: () => {
        operatorsAndPositions.forEach(o => this.addOperatorInternal(o.op, o.pos));
        links.forEach(l => this.addLinkInternal(l));
      },
      undo: () => {
        // remove links
        links.forEach(l => this.deleteLinkWithIDInternal(l.linkID));
        // remove the operators from JointJS
        operatorsAndPositions.forEach(o => this.deleteOperatorInternal(o.op.operatorID));
      }
    };
    this.executeAndStoreCommand(command);
  }

  public deleteOperatorsAndLinks(operatorIDs: string[], linkIDs: string[]): void {
    // save operators to be deleted and their current positions
    const operatorsAndPositions = new Map<OperatorPredicate, Point>();
    operatorIDs.forEach(operatorID => {
      operatorsAndPositions.set(this.getTexeraGraph().getOperator(operatorID),
      this.getJointGraphWrapper().getOperatorPosition(operatorID)
      );
    });
    // save links to be deleted, including links needs to be deleted and links affected by deleted operators
    const linksToDelete = new Set<OperatorLink>();
        // delete links required by this command
        linkIDs.map(linkID => this.getTexeraGraph().getLinkWithID(linkID))
        .forEach(link => linksToDelete.add(link));
    // delete links related to the deleted operator
    this.getTexeraGraph().getAllLinks()
      .filter(link => operatorIDs.includes(link.source.operatorID) || operatorIDs.includes(link.target.operatorID))
      .forEach(link => linksToDelete.add(link));


    const command: Command = {
      execute: () => {
        linksToDelete.forEach(link => this.deleteLinkWithIDInternal(link.linkID));
        operatorIDs.forEach(operatorID => this.deleteOperatorInternal(operatorID));
      },
      undo: () => {
        operatorsAndPositions.forEach((position, operator) => {
          this.addOperatorInternal(operator, position);
        });
        linksToDelete.forEach(link => this.addLinkInternal(link));
      }
    };

    this.executeAndStoreCommand(command);
  }

  // not used I believe
  /**
   * Adds a link to the workflow graph
   * Throws an Error if the link ID or the link with same source and target already exists.
   * @param link
   */
  public addLink(link: OperatorLink): void {
    const command: Command = {
      execute: () => this.addLinkInternal(link),
      undo: () => this.deleteLinkWithIDInternal(link.linkID)
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Deletes a link with the linkID from the workflow graph
   * Throws an Error if the linkID doesn't exist in the workflow graph.
   * @param linkID
   */
  public deleteLinkWithID(linkID: string): void {
    const link = this.getTexeraGraph().getLinkWithID(linkID);
    const command: Command = {
      execute: () => this.deleteLinkWithIDInternal(linkID),
      undo: () => this.addLinkInternal(link)
    };
    this.executeAndStoreCommand(command);
  }

  public deleteLink(source: OperatorPort, target: OperatorPort): void {
    const link = this.getTexeraGraph().getLink(source, target);
    this.deleteLinkWithID(link.linkID);
  }

  // problem here
  public setOperatorProperty(operatorID: string, newProperty: object): void {
    const prevProperty = this.getTexeraGraph().getOperator(operatorID).operatorProperties;
    const command: Command = {
      execute: () => {
        this.jointGraphWrapper.highlightOperator(operatorID);
        this.setOperatorPropertyInternal(operatorID, newProperty);
      },
      undo: () => {
        this.jointGraphWrapper.highlightOperator(operatorID);
        this.setOperatorPropertyInternal(operatorID, prevProperty);
      }
    };
    this.executeAndStoreCommand(command);
  }

  public setOperatorAdvanceStatus(operatorID: string, newShowAdvancedStatus: boolean) {
    const command: Command = {
      execute: () => {
        this.jointGraphWrapper.highlightOperator(operatorID);
        this.setOperatorAdvanceStatusInternal(operatorID, newShowAdvancedStatus);
      },
      undo: () => {
        this.jointGraphWrapper.highlightOperator(operatorID);
        this.setOperatorAdvanceStatusInternal(operatorID, !newShowAdvancedStatus);
      }
    };
    this.executeAndStoreCommand(command);
  }

  private addOperatorInternal(operator: OperatorPredicate, point: Point): void {
    // get the JointJS UI element
    const operatorJointElement = this.jointUIService.getJointOperatorElement(operator, point);
    // add operator to joint graph first
    // if jointJS throws an error, it won't cause the inconsistency in texera graph
    this.jointGraph.addCell(operatorJointElement);
    // add operator to texera graph
    this.texeraGraph.addOperator(operator);
  }

  private deleteOperatorInternal(operatorID: string): void {
    this.texeraGraph.assertOperatorExists(operatorID);
    // remove the operator from JointJS
    this.jointGraph.getCell(operatorID).remove();
    // JointJS operator delete event will propagate and trigger Texera operator delete
  }

  private addLinkInternal(link: OperatorLink): void {
    this.texeraGraph.assertLinkNotExists(link);
    this.texeraGraph.assertLinkIsValid(link);
    // add the link to JointJS
    const jointLinkCell = JointUIService.getJointLinkCell(link);
    this.jointGraph.addCell(jointLinkCell);
    // JointJS link add event will propagate and trigger Texera link add
  }

  private deleteLinkWithIDInternal(linkID: string): void {
    this.texeraGraph.assertLinkWithIDExists(linkID);
    this.jointGraph.getCell(linkID).remove();
    // JointJS link delete event will propagate and trigger Texera link delete
  }

  // use this to modify properties
  private setOperatorPropertyInternal(operatorID: string, newProperty: object) {
    this.texeraGraph.setOperatorProperty(operatorID, newProperty);
  }

  private setOperatorPositionInternal(operatorID: string, newPosition: Point) {
    (this.jointGraph.getCell(operatorID) as joint.dia.Element).position(newPosition.x, newPosition.y);
  }

  private setOperatorAdvanceStatusInternal(operatorID: string, newShowAdvancedStatus: boolean) {
    this.texeraGraph.setOperatorAdvanceStatus(operatorID, newShowAdvancedStatus);
  }

  private executeAndStoreCommand(command: Command): void {
    this.undoRedoService.setListenJointCommand(false);
    command.execute();
    this.undoRedoService.addCommand(command);
    this.undoRedoService.setListenJointCommand(true);
  }

}
