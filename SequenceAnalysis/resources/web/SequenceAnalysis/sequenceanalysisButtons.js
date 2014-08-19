/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('SequenceAnalysis.Buttons');

SequenceAnalysis.Buttons = new function(){
    return {
        viewQuery: function(dataRegionName, options){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            options = options || {};

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

        //    Ext4.Msg.confirm('Type of Search', 'View search panel?  Hit cancel to view all records.', function(button, val, window){
        //        if (button == 'yes'){
        //            var params = {
        //                schemaName: options.schemaName ? options.schemaName : 'sequenceanalysis',
        //                'query.queryName': options.queryName
        //            };
        //
        //            params['query.'+(options.keyField ? options.keyField :'analysis_id')+'~in'] = checked.join(';');
        //
        //            changeLocation(LABKEY.ActionURL.buildURL(
        //                    'query',
        //                    'executeQuery.view',
        //                    LABKEY.ActionURL.getContainer(),
        //                    params
        //                ));
        //        }
        //        else {
                    var params = {
                        schemaName: options.schemaName ? options.schemaName : 'sequenceanalysis',
                        'query.queryName': options.queryName
                    };

                    params['query.'+(options.keyField ? options.keyField :'analysis_id')+'~in'] = checked.join(';');

                    changeLocation(LABKEY.ActionURL.buildURL(
                            'query',
                            'executeQuery.view',
                            LABKEY.ActionURL.getContainer(),
                            params
                    ));
        //        }
        //    }, this);

            function changeLocation(location){
                window.location = location;
            }
        },

        /**
         * The button handler used to deactivate records, such as SNPs and alignments, from a dataRegion.  Disabled due to
         * the inability to update records across containers in a single API call, which makes workbooks difficult
         */
        deactivateRecords: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.confirm("Deactivate Rows", "You are about to deactivate the selected rows.  They will no longer show up in reports.  Are you sure you want to do this?", function(button){
                if (button == 'yes'){
                    var toUpdate = [];
                    Ext4.each(checked, function(pk){
                        toUpdate.push({rowid: pk, status: false});
                    });

                    LABKEY.Query.updateRows({
                        schemaName: dataRegion.schemaName,
                        queryName: dataRegion.queryName,
                        rows: toUpdate,
                        success: function(result){
                            console.log('Success');
                        },
                        failure: LDK.Utils.getErrorCallback()
                    });
                }
            }, this);
        },

        /**
         * The button handler used to activate records, such as SNPs and alignments, from a dataRegion.  Disabled due to
         * the inability to update records across containers in a single API call, which makes workbooks difficult
         */
        activateRecords: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.confirm("Activate Rows", "You are able to activate the selected rows.  They will now show up in reports.  Are you sure you want to do this?", function(button){
                if (button == 'yes'){
                    var toUpdate = [];
                    Ext4.each(checked, function(pk){
                        toUpdate.push({rowid: pk, status: true});
                    });

                    LABKEY.Query.updateRows({
                        schemaName: dataRegion.schemaName,
                        queryName: dataRegion.queryName,
                        rows: toUpdate,
                        success: function(result){
                            console.log('Success');
                        },
                        failure: LDK.Utils.getErrorCallback()
                    });
                }
            }, this);
        },

        viewFiles: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            checked = checked.sort();
            LABKEY.Query.selectRows({
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_analyses',
                filters: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'runid',
                scope: this,
                success: function(data){
                    if (!data || !data.rows)
                        return;

                    var runIds = [];
                    Ext4.each(data.rows, function(row){
                        runIds.push(row.runid);
                    }, this);


                    var params = {
                        schemaName: 'exp',
                        'query.queryName': 'data'
                    };

                    params['query.run~in'] = runIds.join(';');

                    changeLocation(LABKEY.ActionURL.buildURL(
                            'query',
                            'executeQuery.view',
                            LABKEY.ActionURL.getContainer(),
                            params
                        ));


                },
                failure: LDK.Utils.getErrorCallback()
            });

            function changeLocation(location){
                window.location = location;
            }

        },

        viewSNPs: function(dataRegionName, options){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            options = options || {};

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL(
                'sequenceanalysis',
                'snp_viewer.view',
                LABKEY.ActionURL.getContainer(),
                {analysisIds: checked.join(';')}
            );
        },

        viewSNPDetails: function(dataRegionName, options){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            options = options || {};

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL(
                'sequenceanalysis',
                'snp_haplotype.view',
                LABKEY.ActionURL.getContainer(),
                {analysisIds: checked.join(';')}
            );
        },

        performAnalysis: function(dataRegionName, btnEl){
            if (!LABKEY.Security.currentUser.canUpdate){
                alert('You do not have permission to analyze data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.create('LABKEY.ext.ImportWizardWin', {
                controller: 'sequenceanalysis',
                action: 'alignmentAnalysis',
                urlParams: {
                    path: './',
                    analyses: checked.join(';')
                },
                workbookFolderType: Laboratory.Utils.getDefaultWorkbookFolderType()
            }).show(btnEl);
        },

        performAnalysisFromReadsets: function(dataRegionName, btnEl){
            if (!LABKEY.Security.currentUser.canUpdate){
                alert('You do not have permission to analyze data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.create('LABKEY.ext.ImportWizardWin', {
                controller: 'sequenceanalysis',
                action: 'sequenceAnalysis',
                urlParams: {
                    //taskId: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline',
                    path: './',
                    readsets: checked.join(';')
                },
                workbookFolderType: Laboratory.Utils.getDefaultWorkbookFolderType()
            }).show(btnEl);
        },

        deactivateAlignments: function(dataRegionName, btnEl){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }
//            if (checked.length != 1){
//                alert('Can only edit one row at a time');
//                return;
//            }

            Ext4.Msg.wait('Loading...');

            var referenceMap = {};
            var readCountMap = {};
            var junctionRecordMap = {};
            var alignmentIdMap = {};
            var inactiveAlignmentIdMap = {};
            var alleleCombinations = {};

            var multi = new LABKEY.MultiRequest();
            Ext4.each(checked, function(id){
                multi.add(LABKEY.Query.selectRows, {
                    schemaName: 'SequenceAnalysis',
                    queryName: 'alignment_summary_junction',
                    timeout: 0,
                    includeTotalCount: false,
                    columns: 'rowid,analysis_id,alignment_id,alignment_id/total,ref_nt_id,ref_nt_id/name,alignment_id/container,alignment_id/container/path,status',
                    filterArray: [
                        LABKEY.Filter.create('alignment_id', id.replace(/,/g, ';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                    ],
                    scope: this,
                    success: function(data){
                        if (data.rows && data.rows.length){
                            Ext4.each(data.rows, function(row){
                                if (!junctionRecordMap[row.alignment_id])
                                    junctionRecordMap[row.alignment_id] = [];

                                junctionRecordMap[row.alignment_id].push(row);

                                var ref_name = row['ref_nt_id/name'];
                                if (!referenceMap[ref_name]){
                                    referenceMap[ref_name] = {
                                        rowid: ref_name,
                                        name: row['ref_nt_id/name']
                                    }
                                }

                                var alignment_id = row['alignment_id'];
                                if (!readCountMap[alignment_id])
                                    readCountMap[alignment_id] = row['alignment_id/total'];

                                if (!alignmentIdMap[alignment_id])
                                    alignmentIdMap[alignment_id] = [];
                                if (!inactiveAlignmentIdMap[alignment_id])
                                    inactiveAlignmentIdMap[alignment_id] = [];

                                if (row.status){
                                    alignmentIdMap[alignment_id].push(row['ref_nt_id/name']);
                                }
                                else {
                                    inactiveAlignmentIdMap[alignment_id].push(row['ref_nt_id/name']);
                                }
                            }, this);

                            for (var alignmentId in alignmentIdMap){
                                var alleleSet = alignmentIdMap[alignmentId];
                                alleleSet = Ext4.unique(alleleSet);
                                alleleSet = alleleSet.sort();
                                alleleSet = alleleSet.join(';');
                                if (!alleleCombinations[alleleSet]){
                                    alleleCombinations[alleleSet] = {
                                        active: alignmentIdMap[alignmentId],
                                        inactive: [],
                                        alignmentIds: [],
                                        alleles: {},
                                        inactiveAlleles: {}
                                    }
                                }
                                alleleCombinations[alleleSet].alignmentIds.push(alignmentId);

                                if (junctionRecordMap[alignmentId]){
                                    Ext4.each(junctionRecordMap[alignmentId], function(row){
                                        var allele = row['ref_nt_id/name'];
                                        if (row.status){
                                            if (!alleleCombinations[alleleSet].alleles[allele])
                                                alleleCombinations[alleleSet].alleles[allele] = {
                                                    total: 0,
                                                    rows: []
                                                };

                                            alleleCombinations[alleleSet].alleles[allele].rows.push(row);
                                            alleleCombinations[alleleSet].alleles[allele].total += row['alignment_id/total'];
                                        }
                                        else {
                                            if (!alleleCombinations[alleleSet].inactiveAlleles[allele])
                                                alleleCombinations[alleleSet].inactiveAlleles[allele] = {
                                                    total: 0,
                                                    rows: []
                                                };

                                            alleleCombinations[alleleSet].inactiveAlleles[allele].rows.push(row);
                                            alleleCombinations[alleleSet].inactiveAlleles[allele].total += row['alignment_id/total'];
                                        }
                                    }, this);
                                }
                            }
                        }
                    },
                    failure: LDK.Utils.getErrorCallback()
                });
            }, this);

            multi.send(onSuccess, this);

            function onSuccess(){
                var items = [];
                var idx = 1;
                for (var alleles in alleleCombinations){
                    var record = alleleCombinations[alleles];
                    var checkboxes = [];

                    var activeCount = 0;
                    var activeIds = Ext4.unique(record.alignmentIds);
                    Ext4.each(activeIds, function(id){
                        activeCount += readCountMap[id];
                    }, this);

                    var keys = Ext4.Object.getKeys(record.alleles);
                    keys = keys.sort();
                    Ext4.Array.forEach(keys, function(refName){
                        checkboxes.push({
                            xtype: 'checkbox',
                            inputValue: refName,
                            boxLabel: refName + ' (' + record.alleles[refName].total + ')',
                            checked: true,
                            rows: record.alleles[refName].rows
                        });
                    }, this);

                    var inactiveKeys = Ext4.Object.getKeys(record.inactiveAlleles);
                    inactiveKeys = inactiveKeys.sort();
                    Ext4.Array.forEach(inactiveKeys, function(refName){
                        checkboxes.push({
                            xtype: 'checkbox',
                            inputValue: refName,
                            boxLabel: refName + ' (' + record.inactiveAlleles[refName].total + ')',
                            checked: false,
                            rows: record.inactiveAlleles[refName].rows
                        });
                    }, this);

                    items.push({
                        border: false,
                        itemId: 'alleleSet' + idx,
                        defaults: {
                            border: false
                        },
                        items: [{
                            html: 'Group ' + idx,
                            style: 'padding-bottom: 5px;'
                        },{
                            layout: 'hbox',
                            style: 'padding-bottom: 5px;padding-left:5px;',
                            items: [{
                                xtype: 'ldk-linkbutton',
                                style: 'margin-left:5px;',
                                text: '[Check All]',
                                handler: function(btn){
                                    btn.up('panel').up('panel').down('checkboxgroup').items.each(function(item){
                                        item.setValue(true);
                                    });
                                }
                            },{
                                border: false,
                                html: '&nbsp;'
                            },{
                                xtype: 'ldk-linkbutton',
                                style: 'margin-left:5px;',
                                text: '[Uncheck All]',
                                handler: function(btn){
                                    btn.up('panel').up('panel').down('checkboxgroup').items.each(function(item){
                                        item.setValue(false);
                                    });
                                }
                            }]
                        },{
                            xtype: 'checkboxgroup',
                            width: 750,
                            style: 'margin-left:5px;',
                            columns: 2,
                            items: checkboxes
                        }]
                    });
                    idx++;
                }

                Ext4.Msg.hide();
                var window = Ext4.create('Ext.window.Window', {
                    modal: true,
                    title: 'Select Alleles',
                    width: 800,
                    maxHeight: 600,
                    autoScroll: true,
                    defaults: {
                        border: false,
                        bodyStyle: 'padding: 5px;'
                    },
                    items: [{
                        xtype: 'form',
                        width: 750,
                        border: false,
                        items: items,
                        defaults: {
                            border: false
                        }
                    }],
                    buttons: [{
                        text: 'Submit',
                        handler: function(btn){
                            var window = btn.up('window');
                            var fields = window.down('form').getForm().getFields();

                            var toUpdate = {};
                            var containerPath = [];
                            fields.each(function(cb){
                                if (cb.xtype == 'checkboxgroup')
                                    return;

                                if (!cb.isDirty())
                                    return;

                                Ext4.each(cb.rows, function(r){
                                    if (!toUpdate[r['alignment_id/container/path']])
                                        toUpdate[r['alignment_id/container/path']] = [];

                                    toUpdate[r['alignment_id/container/path']].push({
                                        rowid: r.rowid,
                                        status: cb.checked
                                    });

                                    containerPath.push(r['alignment_id/container/path']);
                                }, this);
                            }, this);

                            if (!LABKEY.Utils.isEmptyObj(toUpdate)){
                                var multi = new LABKEY.MultiRequest();

                                for(var container in toUpdate){
                                    multi.add(LABKEY.Query.updateRows, {
                                        containerPath: container,
                                        schemaName: 'sequenceanalysis',
                                        queryName: 'alignment_summary_junction',
                                        scope: this,
                                        rows: toUpdate[container],
                                        failure: LDK.Utils.getErrorCallback()
                                    });
                                }
                                multi.send(function(){
                                    window.close();
                                    dataRegion.refresh();
                                    Ext4.Msg.alert('Success', 'Records updated');
                                }, this);
                            }
                            else {
                                window.close();
                            }

                        }
                    },{
                        text: 'Close',
                        handler: function(btn){
                            btn.up('window').close();
                        }
                    }]
                }).show(btnEl);
            }
        },

        generateFastQc: function(dataRegionName, pkName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            pkName = pkName || 'readsets';
            var params = {};
            params[pkName] = checked;

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL(
                'sequenceanalysis',
                'fastqcReport',
                LABKEY.ActionURL.getContainer(),
                params
            );
        },

        generateBamStatsReport: function(dataRegionName, pkName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            pkName = pkName || 'dataIds';
            var params = {};
            params[pkName] = checked;

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL(
                    'sequenceanalysis',
                    'bamStatsReport',
                    LABKEY.ActionURL.getContainer(),
                    params
            );
        },

        generateFastQcForAnalysis: function(dataRegionName, btnEl){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.create('Ext.window.Window', {
                modal: true,
                dataRegionName: dataRegionName,
                dataRegionSelections: checked,
                title: 'FastQC Report',
                width: 300,
                items: [{
                    xtype: 'form',
                    bodyStyle: 'padding: 5px;',
                    border: false,
                    items: [{
                        xtype: 'displayfield',
                        labelWidth: 150,
                        fieldLabel: 'Choose files to include'
                    },{
                        xtype: 'checkboxgroup',
                        itemId: 'chooseFiles',
                        columns: 1,
                        items: [{
                            name: 'chooseFiles',
                            boxLabel: 'Raw Input File(s)',
                            inputValue: 'readset/fileid;readset/fileid2'
                        },{
                            name: 'chooseFiles',
                            boxLabel: 'Processed Input File(s)',
                            inputValue: 'inputfile;inputfile2',
                            checked: true
                        },{
                            name: 'chooseFiles',
                            boxLabel: 'Alignment File',
                            inputValue: 'alignmentfile',
                            checked: true
                        }]
                    }]
                }],
                buttons: [{
                    text: 'Submit',
                    handler: function(btn){
                        var win = btn.up('window');
                        var selections = win.down('#chooseFiles').getValue().chooseFiles;

                        if (!selections || !selections.length){
                            alert("Must choose one or more file types");
                            return;
                        }

                        var props = [];
                        Ext4.each(selections, function(s){
                            s = s.split(';');
                            props = props.concat(s);
                        }, this);

                        LABKEY.Query.selectRows({
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'sequenceanalysis',
                            queryName: 'sequence_analyses',
                            columns: 'rowid,inputfile,inputfile2,alignmentfile,readset/fileid,readset/fileid2',
                            filterArray: [
                                LABKEY.Filter.create('rowid', win.dataRegionSelections.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                            ],
                            scope: this,
                            failure: LDK.Utils.getErrorCallback(),
                            success: function(results){
                                var ids = [];

                                Ext4.each(results.rows, function(row){
                                    Ext4.each(props, function(prop){
                                        if (row[prop])
                                            ids.push(row[prop]);
                                    }, this);
                                }, this);


                                if (ids.length){
                                    window.location = LABKEY.ActionURL.buildURL(
                                        'sequenceanalysis',
                                        'fastqcReport',
                                        LABKEY.ActionURL.getContainer(),
                                        {dataIds: ids}
                                    );
                                }
                            }
                        })

                        win.close();
                    }
                },{
                    text: 'Cancel',
                    handler: function(btn){
                        btn.up('window').close();
                    }
                }]
            }).show(btnEl);
        },

        generateIlluminaSampleFile: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('sequenceanalysis', 'illuminaSampleSheetExport', null, {
                pks: checked,
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_readsets',
                srcURL: LDK.Utils.getSrcURL()
            })
        },

        downloadFilesForReadset: function(dataRegionName, btnEl){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Loading...');

            LABKEY.Query.selectRows({
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_readsets',
                filterArray: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'rowid,fileid,fileid2',
                scope: this,
                success: function(result){
                    Ext4.Msg.hide();

                    var ids = [];
                    if (result && result.rows.length){
                        Ext4.create('SequenceAnalysis.window.RunExportWindow', {
                            dataRegionName: dataRegionName,
                            records: result.rows,
                            fileTypes: {
                                'Forward Reads': ['fileid'],
                                'Reverse Reads': ['fileid2']
                            }
                        }).show(btnEl);
                    }
                },
                failure: LDK.Utils.getErrorCallback()
            });
        },

        downloadFilesForAnalysis: function(dataRegionName, btnEl){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Loading...');

            LABKEY.Query.selectRows({
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_analyses',
                filterArray: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'rowid,inputfile,inputfile2,readset/fileid,readset/fileid2,alignmentfile,alignmentfileindex,reference_library',
                scope: this,
                success: function(result){
                    Ext4.Msg.hide();

                    var ids = [];
                    if (result && result.rows.length){
                        Ext4.create('SequenceAnalysis.window.RunExportWindow', {
                            dataRegionName: dataRegionName,
                            records: result.rows,
                            fileTypes: {
                                'Raw Input File(s)': ['readset/fileid','readset/fileid2'],
                                'Processed Input File(s)': ['inputfile','inpitfile2'],
                                'Alignment File': ['alignmentfile', 'alignmentfileindex'],
                                'Reference Genome': ['reference_library']
                            }
                        }).show(btnEl);
                    }
                },
                failure: LDK.Utils.getErrorCallback()
            });
        },

        deleteTable: function(dataRegionName){
            if (!LABKEY.Security.currentUser.canDelete){
                alert('You do not have permission to delete data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('sequenceanalysis', 'deleteRecords', null, {
                schemaName: dataRegion.schemaName,
                'query.queryName': dataRegion.queryName,
                dataRegionSelectionKey: 'query',
                '.select': checked,
                returnURL: window.location.pathname + window.location.search
            });
        },

        importApplicationHandler: function(dataRegionName, btn){
            Ext4.create('SequenceAnalysis.window.IlluminaApplicationWindow', {
                dataRegionName: dataRegionName
            }).show(btn);
        },

        importSampleKitHandler: function(dataRegionName, btn){
            Ext4.create('SequenceAnalysis.window.IlluminaSampleKitWindow', {
                dataRegionName: dataRegionName
            }).show(btn);
        },

        compareVariantsHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                Ext4.Msg.alert('Error', 'Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('sequenceanalysis', 'variantComparison', null, {
                fileIds: checked
            });
        },

        jointGenotypingHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                Ext4.Msg.alert('Error', 'Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('sequenceanalysis', 'jointGenotyping', null, {
                fileIds: checked
            });
        }
    }
};