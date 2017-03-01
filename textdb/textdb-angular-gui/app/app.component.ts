import { Component, ViewChild } from '@angular/core';

import { TheFlowchartComponent } from './the-flowchart.component';
import { OperatorBarComponent } from './operator-bar.component';

import { DataService } from './data-service';
import { Data } from './data';

declare var jQuery: any;

@Component({
    moduleId: module.id,
    selector: 'my-app',
    template: `
		<nav the-navbar id="css-navbar" class="navbar navbar-toggleable-md navbar-light bg-faded"></nav>
		<nav operator-bar id="css-operator-bar" class="navbar navbar-toggleable-md navbar-light bg-faded" #theOperatorBar></nav>
		<flowchart-container class="container fill" #theFlowchart></flowchart-container>
	`,
    providers: [DataService],
    styleUrls: ['style.css']
})
export class AppComponent {
	name = 'Angular';
	data : Data;
	promiseCompleted = false;

    constructor(private dataService: DataService) { }

    getData(): void {
        this.dataService.getData().then(
            data => {
                this.data = data[0];
                this.promiseCompleted = true;
            },
            error => {
                console.log(error);
            }
        );
    }

    @ViewChild('theFlowchart') theFlowchart: TheFlowchartComponent;
    @ViewChild('theOperatorBar') theOperatorBar: OperatorBarComponent;

    ngAfterViewInit() {
        var current = this;
        current.dataService.getData().then(
            data => {
                current.data = data[0].jsonData;
                jQuery(document).ready(function() {
                    current.theFlowchart.initialize(current.data);
                });
            },
            error => {
                console.log(error);
            }
        );
    }
}
